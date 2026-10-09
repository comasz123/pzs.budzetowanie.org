package pl.ngo.budget.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Czyta arkusz „Budżet” wniosku PKO BP „Korzenie Jutra”: pozycje kosztów z podziałem na lata 2026/2027
 * oraz częścią finansowaną wkładem własnym OSIF (kolumna I).
 */
public final class KorzenieJutraWorkbookParser {

    public static final String SHEET_NAME = "Budżet";

    private KorzenieJutraWorkbookParser() {
    }

    /**
     * @param amountByYear wartość kosztu w roku (kolumna H)
     * @param unitsByYear  liczba jednostek w roku (kolumna G), np. miesięcy
     * @param unitCost     koszt jednostkowy (kolumna F, z pierwszego roku pozycji)
     * @param osifAmount   część pozycji finansowana wkładem własnym OSIF
     */
    public record Line(String name, String costType, String unit, Map<Integer, BigDecimal> amountByYear,
                       Map<Integer, BigDecimal> unitsByYear, BigDecimal unitCost, BigDecimal osifAmount) {
        public BigDecimal total() {
            return amountByYear.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    public record ParseResult(List<Line> lines, BigDecimal grandTotal, BigDecimal osifTotal, List<String> warnings) {
    }

    public static ParseResult parse(InputStream in) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                throw new IllegalArgumentException("Brak arkusza „" + SHEET_NAME + "”.");
            }
            return parse(sheet);
        }
    }

    static ParseResult parse(Sheet sheet) {
        List<Line> lines = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        BigDecimal grandTotal = null;
        BigDecimal osifTotal = null;
        boolean open = false;
        String type = null;
        String unit = null;
        String name = null;
        BigDecimal unitCost = null;
        Map<Integer, BigDecimal> amounts = null;
        Map<Integer, BigDecimal> units = null;
        BigDecimal osif = BigDecimal.ZERO;
        // Sumy działań (wiersze nagłówkowe): numer pierwszej pozycji działania, nazwa i oczekiwana suma.
        List<Object[]> sections = new ArrayList<>();

        for (int r = 2; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String label = text(row.getCell(1));
            Cell yearCell = row.getCell(4);
            boolean hasYear = yearCell != null && yearCell.getCellType() == CellType.NUMERIC;

            if ("RAZEM".equalsIgnoreCase(text(row.getCell(6)))) {
                grandTotal = EmployeeCostWorkbookParser.number(row.getCell(7));
                continue;
            }
            if ("wkład własny".equalsIgnoreCase(text(row.getCell(7)))) {
                osifTotal = EmployeeCostWorkbookParser.number(row.getCell(8));
                continue;
            }
            if (!hasYear) {
                BigDecimal sectionTotal = label == null ? null : EmployeeCostWorkbookParser.number(row.getCell(7));
                if (sectionTotal != null && sectionTotal.signum() != 0) {
                    sections.add(new Object[]{lines.size() + (open ? 1 : 0), label, sectionTotal});
                }
                continue; // nagłówek sekcji (suma działania) albo podsumowanie
            }

            int year = (int) yearCell.getNumericCellValue();
            if (label != null) {
                if (open) {
                    lines.add(finish(name, type, unit, amounts, units, unitCost, osif));
                }
                name = label;
                type = text(row.getCell(2));
                unit = text(row.getCell(3));
                unitCost = EmployeeCostWorkbookParser.number(row.getCell(5));
                amounts = new LinkedHashMap<>();
                units = new LinkedHashMap<>();
                osif = BigDecimal.ZERO;
                open = true;
            } else if (!open) {
                continue;
            }
            amounts.merge(year, EmployeeCostWorkbookParser.number(row.getCell(7)), BigDecimal::add);
            units.merge(year, EmployeeCostWorkbookParser.number(row.getCell(6)), BigDecimal::add);
            osif = osif.add(EmployeeCostWorkbookParser.number(row.getCell(8)));
        }
        if (open) {
            lines.add(finish(name, type, unit, amounts, units, unitCost, osif));
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Nie znaleziono pozycji kosztów w arkuszu „" + SHEET_NAME + "”.");
        }
        for (int i = 0; i < sections.size(); i++) {
            int from = (Integer) sections.get(i)[0];
            int to = i + 1 < sections.size() ? (Integer) sections.get(i + 1)[0] : lines.size();
            BigDecimal expected = (BigDecimal) sections.get(i)[2];
            BigDecimal actual = lines.subList(from, to).stream().map(Line::total).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (actual.subtract(expected).abs().compareTo(new BigDecimal("0.01")) > 0) {
                warnings.add("Działanie „" + sections.get(i)[1] + "”: suma pozycji " + actual
                        + " ≠ suma działania w arkuszu " + expected + ".");
            }
        }
        BigDecimal sum = lines.stream().map(Line::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (grandTotal != null && sum.subtract(grandTotal).abs().compareTo(new BigDecimal("0.01")) > 0) {
            warnings.add("Suma pozycji " + sum + " ≠ RAZEM w arkuszu " + grandTotal + ".");
        }
        BigDecimal osifSum = lines.stream().map(Line::osifAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (osifTotal != null && osifSum.subtract(osifTotal).abs().compareTo(new BigDecimal("0.01")) > 0) {
            warnings.add("Suma wkładu OSIF w pozycjach " + osifSum + " ≠ wkład własny w arkuszu " + osifTotal + ".");
        }
        return new ParseResult(lines, sum, osifSum, warnings);
    }

    private static Line finish(String name, String type, String unit, Map<Integer, BigDecimal> amounts,
                               Map<Integer, BigDecimal> units, BigDecimal unitCost, BigDecimal osif) {
        return new Line(name, type, unit, amounts, units, unitCost, osif);
    }

    private static String text(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.STRING) {
            String s = cell.getStringCellValue().replace(' ', ' ').trim().replaceAll("\\s+", " ");
            return s.isEmpty() ? null : s;
        }
        return null;
    }
}
