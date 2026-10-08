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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Czyta z arkusza „Koszty” pozostałe koszty (poza wynagrodzeniami pracowników): ekwiwalenty oraz
 * „Koszty stałe pozostałe” (księgowość, czynsze, media, oprogramowanie…), po 12 miesięcy i z rozbiciem
 * na źródła finansowania. Kwota miesiąca to koszt z pierwszej kolumny bloku, a gdy jest pusta — suma źródeł.
 */
public final class OtherCostWorkbookParser {

    public static final String GROUP_BIURO = "BIURO";
    public static final String GROUP_POZOSTALE = "POZOSTALE";
    public static final String GROUP_WYNAGRODZENIA = "WYNAGRODZENIA";

    private static final String[] OFFICE_WORDS = {
            "czynsz", "smieci", "śmieci", "prąd", "internet", "orange", "sprzątanie", "upc",
            "materiały biurowe", "art. spożywcze", "pocztowe", "transportowe", "wyposażenie", "ochrona mienia"};

    private OtherCostWorkbookParser() {
    }

    public record CostLine(int sheetRow, String label, String group, List<BigDecimal> months,
                           Map<String, List<BigDecimal>> sources) {
        public BigDecimal total() {
            return months.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    public record ParseResult(List<CostLine> lines, List<String> warnings) {
    }

    public static ParseResult parse(InputStream in) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheet(EmployeeCostWorkbookParser.SHEET_NAME);
            if (sheet == null) {
                throw new IllegalArgumentException("Brak arkusza „" + EmployeeCostWorkbookParser.SHEET_NAME + "”.");
            }
            return parse(sheet);
        }
    }

    static ParseResult parse(Sheet sheet) {
        int[] monthColumns = EmployeeCostWorkbookParser.findMonthColumns(sheet);
        List<CostLine> lines = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String section = null;
        String previousLabel = null;
        int previousRow = -1;
        Map<String, Integer> seen = new HashMap<>();
        int skippedEmpty = 0;

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            int sheetRow = r + 1;
            Cell labelCell = row.getCell(0);
            String label = labelCell != null && labelCell.getCellType() == CellType.STRING
                    ? EmployeeCostWorkbookParser.clean(labelCell.getStringCellValue()) : null;
            String key = label == null ? null : label.toLowerCase(Locale.ROOT);

            if (key != null) {
                if (key.equals("ekwiwalenty")) {
                    section = GROUP_WYNAGRODZENIA;
                    continue;
                }
                if (key.startsWith("koszty stałe pozostałe")) {
                    section = "OTHER";
                    continue;
                }
                if (key.startsWith("koszty stałe osobowe") || key.equals("umowy o pracę") || key.equals("umowy zlecenia")) {
                    section = null;
                    continue;
                }
                if (key.startsWith("oprogramowanie koszty roczne")) {
                    continue;
                }
            }
            if (section == null) {
                continue;
            }

            List<BigDecimal> totals = new ArrayList<>(12);
            Map<String, List<BigDecimal>> sources = new LinkedHashMap<>();
            List<BigDecimal> months = new ArrayList<>(12);
            for (int m = 0; m < 12; m++) {
                totals.add(EmployeeCostWorkbookParser.number(row.getCell(monthColumns[m])));
                int[] range = EmployeeCostWorkbookParser.sourceRange(monthColumns, m);
                BigDecimal sourceSum = BigDecimal.ZERO;
                for (int col = range[0]; col < range[1]; col++) {
                    String header = EmployeeCostWorkbookParser.headerAt(sheet, col);
                    if (header == null) {
                        break;
                    }
                    var source = FundingSources.forHeader(header);
                    if (source.isEmpty()) {
                        String note = "Nieznane źródło finansowania w nagłówku: „" + header + "” — pominięto.";
                        if (!warnings.contains(note)) {
                            warnings.add(note);
                        }
                        continue;
                    }
                    BigDecimal value = EmployeeCostWorkbookParser.number(row.getCell(col));
                    if (value.signum() != 0) {
                        List<BigDecimal> series = sources.computeIfAbsent(source.get().key(),
                                k -> EmployeeCostWorkbookParser.zeros());
                        series.set(m, series.get(m).add(value));
                        sourceSum = sourceSum.add(value);
                    }
                }
                months.add(totals.get(m).signum() != 0 ? totals.get(m) : sourceSum);
            }
            boolean hasValues = months.stream().anyMatch(v -> v.signum() != 0);

            String useLabel = label;
            if (label == null) {
                if (!hasValues || previousLabel == null) {
                    continue;
                }
                useLabel = previousLabel;
                // Wiersz bez nazwy pod wierszem z nazwą to jego dalszy ciąg; nagłówek z tą samą kwotą wypada.
                if (!lines.isEmpty() && lines.get(lines.size() - 1).sheetRow() == previousRow
                        && repeats(lines.get(lines.size() - 1).months(), months)) {
                    lines.remove(lines.size() - 1);
                }
            } else {
                previousLabel = label;
                previousRow = sheetRow;
            }
            if (!hasValues) {
                skippedEmpty++;
                continue;
            }

            String normalized = useLabel.toLowerCase(Locale.ROOT);
            Integer earlier = seen.get(normalized);
            if (earlier != null) {
                CostLine first = lines.stream().filter(l -> l.sheetRow() == earlier).findFirst().orElse(null);
                if (first != null) {
                    BigDecimal firstSources = sourceTotal(first.sources());
                    BigDecimal thisSources = sourceTotal(sources);
                    if (thisSources.compareTo(firstSources) > 0) {
                        lines.remove(first);
                        warnings.add("Wiersze " + earlier + " i " + sheetRow + " mają tę samą nazwę „" + useLabel
                                + "” — zaimportowano wiersz " + sheetRow + " (pełniejsze źródła), pominięto " + earlier + ".");
                    } else {
                        warnings.add("Wiersze " + earlier + " i " + sheetRow + " mają tę samą nazwę „" + useLabel
                                + "” — zaimportowano wiersz " + earlier + ", pominięto " + sheetRow + ".");
                        continue;
                    }
                }
            }
            seen.put(normalized, sheetRow);
            String group = GROUP_WYNAGRODZENIA.equals(section) ? GROUP_WYNAGRODZENIA : groupFor(normalized);
            lines.add(new CostLine(sheetRow, useLabel, group, months, sources));
        }
        if (skippedEmpty > 0) {
            warnings.add("Pominięto " + skippedEmpty + " pustych pozycji (bez żadnych kwot w 2026).");
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Nie znaleziono pozostałych kosztów w arkuszu „Koszty”.");
        }
        return new ParseResult(lines, warnings);
    }

    static String groupFor(String normalizedLabel) {
        for (String word : OFFICE_WORDS) {
            if (normalizedLabel.contains(word)) {
                return GROUP_BIURO;
            }
        }
        return GROUP_POZOSTALE;
    }

    /** Czy wiersz-nagłówek powtarza tylko kwoty z wiersza pod nim (te same wartości w miesiącach, w których je ma). */
    private static boolean repeats(List<BigDecimal> header, List<BigDecimal> detail) {
        boolean any = false;
        for (int m = 0; m < 12; m++) {
            if (header.get(m).signum() != 0) {
                any = true;
                if (header.get(m).compareTo(detail.get(m)) != 0) {
                    return false;
                }
            }
        }
        return any;
    }

    private static BigDecimal sourceTotal(Map<String, List<BigDecimal>> sources) {
        BigDecimal sum = BigDecimal.ZERO;
        for (List<BigDecimal> series : sources.values()) {
            for (BigDecimal v : series) {
                sum = sum.add(v);
            }
        }
        return sum;
    }
}
