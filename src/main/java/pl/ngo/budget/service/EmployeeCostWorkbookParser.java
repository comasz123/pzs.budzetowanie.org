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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Czyta z arkusza „Koszty” (budżet roczny z blokami miesięcznymi) miesięczny koszt każdego pracownika
 * z sekcji „Umowy o pracę” i „Umowy zlecenia”.
 * <p>
 * Kolumny miesięcy wykrywane są po nagłówkach w wierszu 2 (Styczeń, Luty, …); w pierwszej kolumnie
 * bloku miesiąca stoi łączny koszt pracownika w tym miesiącu (dalsze kolumny to rozbicie na źródła).
 */
public final class EmployeeCostWorkbookParser {

    public static final String SHEET_NAME = "Koszty";

    private static final int MONTH_HEADER_ROW = 1;
    private static final int ANNUAL_COLUMN = 1;
    private static final String[] MONTHS = {
            "styczeń", "luty", "marzec", "kwiecień", "maj", "czerwiec",
            "lipiec", "sierpień", "wrzesień", "październik", "listopad", "grudzień"
    };

    /**
     * Znane błędy w nazwach w kolumnie A arkusza „Koszty” → poprawna nazwa „Nazwisko Imię”.
     * Wiersz Franke Kinga ma w A liczbę 1562.27 (nazwa potwierdzona arkuszem Franke_K i sumą 27 519,63).
     */
    private static final Map<String, String> KNOWN_NAME_FIXES = Map.of(
            "1562.27", "Franke Kinga",
            "Majka Wiśniewska", "Wiśniewska Majka");

    private EmployeeCostWorkbookParser() {
    }

    public enum Contract { UMOWA_O_PRACE, UMOWA_ZLECENIE }

    /**
     * @param sources miesięczne kwoty z kolumn źródeł finansowania: klucz źródła → 12 miesięcy
     */
    public record EmployeeCostRow(int sheetRow, Contract contract, String lastName, String firstName,
                                  List<BigDecimal> months, BigDecimal annualFromSheet,
                                  Map<String, List<BigDecimal>> sources) {
        public BigDecimal total() {
            return months.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    public record ParseResult(List<EmployeeCostRow> rows, List<String> warnings) {
    }

    /**
     * @param nameOverrides numer wiersza arkusza (1-based) → „Nazwisko Imię”, dla wierszy z błędną lub
     *                      odwróconą nazwą
     */
    public static ParseResult parse(InputStream in, Map<Integer, String> nameOverrides) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                throw new IllegalArgumentException("Brak arkusza „" + SHEET_NAME + "”.");
            }
            return parse(sheet, nameOverrides == null ? Map.of() : nameOverrides);
        }
    }

    static ParseResult parse(Sheet sheet, Map<Integer, String> nameOverrides) {
        int[] monthColumns = findMonthColumns(sheet);
        List<EmployeeCostRow> rows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Contract section = null;

        for (int r = MONTH_HEADER_ROW + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            int sheetRow = r + 1;
            Cell labelCell = row.getCell(0);
            String label = labelCell != null && labelCell.getCellType() == CellType.STRING
                    ? clean(labelCell.getStringCellValue()) : null;

            if (label != null) {
                String key = label.toLowerCase(Locale.ROOT);
                if (key.equals("umowy o pracę")) {
                    section = Contract.UMOWA_O_PRACE;
                    continue;
                }
                if (key.equals("umowy zlecenia")) {
                    section = Contract.UMOWA_ZLECENIE;
                    continue;
                }
                if (key.startsWith("ekwiwalent") || key.startsWith("koszty stałe pozostałe")) {
                    if (key.startsWith("ekwiwalenty")) {
                        warnings.add("Wiersz " + sheetRow + ": sekcja „Ekwiwalenty” pominięta (to nie koszty miesięczne pracowników).");
                    }
                    section = null;
                    continue;
                }
            }
            if (section == null) {
                continue;
            }

            String rawName = nameOverrides.get(sheetRow);
            if (rawName == null) {
                rawName = label != null ? label : (labelCell != null && labelCell.getCellType() == CellType.NUMERIC
                        ? describe(labelCell) : null);
                rawName = KNOWN_NAME_FIXES.getOrDefault(rawName, rawName);
            }
            List<BigDecimal> months = new ArrayList<>(12);
            for (int m = 0; m < 12; m++) {
                months.add(number(row.getCell(monthColumns[m])));
            }
            BigDecimal annual = number(row.getCell(ANNUAL_COLUMN));
            boolean hasValues = months.stream().anyMatch(v -> v.signum() != 0) || annual.signum() != 0;

            if (!hasValues) {
                continue;
            }
            String[] parts;
            if (rawName == null) {
                parts = new String[] {"NN", "NN"};
                warnings.add("Wiersz " + sheetRow + ": brak nazwiska w kolumnie A (wartość: " + describe(labelCell)
                        + "), zapisano jako NN NN.");
            } else {
                parts = splitName(rawName);
                if (parts == null) {
                    parts = new String[] {rawName, "NN"};
                    warnings.add("Wiersz " + sheetRow + ": nie da się rozdzielić nazwy „" + rawName
                            + "” na nazwisko i imię, zapisano jako nazwisko „" + rawName + "”, imię NN.");
                }
            }
            Map<String, List<BigDecimal>> sources = new java.util.LinkedHashMap<>();
            for (int m = 0; m < 12; m++) {
                int[] range = sourceRange(monthColumns, m);
                for (int col = range[0]; col < range[1]; col++) {
                    String header = headerAt(sheet, col);
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
                    BigDecimal value = number(row.getCell(col));
                    if (value.signum() != 0) {
                        List<BigDecimal> series = sources.computeIfAbsent(source.get().key(), k -> zeros());
                        series.set(m, series.get(m).add(value));
                    }
                }
            }
            EmployeeCostRow parsed = new EmployeeCostRow(sheetRow, section, parts[0], parts[1], months, annual, sources);
            if (parsed.total().subtract(annual).abs().compareTo(new BigDecimal("0.01")) > 0) {
                warnings.add("Wiersz " + sheetRow + " (" + parts[0] + " " + parts[1] + "): suma miesięcy "
                        + parsed.total() + " ≠ kolumna B (roczne) " + annual + ". Zaimportowano sumę miesięcy.");
            }
            rows.add(parsed);
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Nie znaleziono żadnego pracownika w sekcjach „Umowy o pracę” / „Umowy zlecenia”.");
        }
        return new ParseResult(rows, warnings);
    }

    static List<BigDecimal> zeros() {
        List<BigDecimal> list = new ArrayList<>(12);
        for (int i = 0; i < 12; i++) {
            list.add(BigDecimal.ZERO.setScale(2));
        }
        return list;
    }

    /** Kolumny źródeł w bloku miesiąca: od kolumny po sumie do następnego bloku (wyłącznie). */
    static int[] sourceRange(int[] monthColumns, int m) {
        int start = monthColumns[m] + 1;
        int end = m < 11 ? monthColumns[m + 1] : start + 25;
        return new int[] {start, end};
    }

    /** Nagłówek źródła z wiersza 3; null, gdy pusty, liczbowy albo „Weryfikacja” (koniec bloku). */
    static String headerAt(Sheet sheet, int col) {
        Row header = sheet.getRow(2);
        Cell cell = header == null ? null : header.getCell(col);
        if (cell == null || cell.getCellType() != CellType.STRING) {
            return null;
        }
        String text = clean(cell.getStringCellValue());
        return text == null || text.equalsIgnoreCase("weryfikacja") ? null : text;
    }

    static int[] findMonthColumns(Sheet sheet) {
        Row header = sheet.getRow(MONTH_HEADER_ROW);
        int[] columns = new int[12];
        java.util.Arrays.fill(columns, -1);
        if (header != null) {
            for (Cell cell : header) {
                if (cell.getCellType() != CellType.STRING) {
                    continue;
                }
                String text = clean(cell.getStringCellValue());
                if (text == null) {
                    continue;
                }
                String key = text.toLowerCase(Locale.ROOT);
                for (int m = 0; m < 12; m++) {
                    if (MONTHS[m].equals(key) && columns[m] < 0) {
                        columns[m] = cell.getColumnIndex();
                    }
                }
            }
        }
        for (int m = 0; m < 12; m++) {
            if (columns[m] < 0) {
                throw new IllegalArgumentException("Nie znaleziono kolumny miesiąca „" + MONTHS[m]
                        + "” w wierszu " + (MONTH_HEADER_ROW + 1) + " arkusza „" + SHEET_NAME + "”.");
            }
        }
        return columns;
    }

    /** „Nazwisko Imię” → {nazwisko, imię}; pierwszy człon to nazwisko, reszta imię. */
    static String[] splitName(String raw) {
        String cleaned = clean(raw);
        if (cleaned == null) {
            return null;
        }
        String[] tokens = cleaned.split("\\s+");
        if (tokens.length < 2) {
            return null;
        }
        String last = titleCase(tokens[0]);
        StringBuilder first = new StringBuilder();
        for (int i = 1; i < tokens.length; i++) {
            if (i > 1) {
                first.append(' ');
            }
            first.append(titleCase(tokens[i]));
        }
        return new String[] {last, first.toString()};
    }

    /** „ZYCH” → „Zych”, „Krawczyk-Duda” zostaje; nazwy już w poprawnej wielkości nie są zmieniane. */
    private static String titleCase(String token) {
        if (!token.equals(token.toUpperCase(Locale.ROOT)) || token.length() < 2) {
            return token;
        }
        StringBuilder out = new StringBuilder();
        boolean start = true;
        for (char c : token.toCharArray()) {
            out.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c));
            start = c == '-';
        }
        return out.toString();
    }

    static BigDecimal number(Cell cell) {
        if (cell == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
            case STRING -> parseText(cell.getStringCellValue());
            default -> BigDecimal.ZERO.setScale(2);
        };
    }

    /** Liczby zapisane tekstem: „1 038,71 ”, „55,32 ”, „1,900.71 ”. */
    public static BigDecimal parseNumberText(String text) {
        return parseText(text);
    }

    static BigDecimal parseText(String text) {
        String s = text == null ? "" : text.replace(' ', ' ').replace(" ", "");
        if (s.isEmpty() || s.equals("-")) {
            return BigDecimal.ZERO.setScale(2);
        }
        int comma = s.lastIndexOf(',');
        int dot = s.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            s = comma > dot ? s.replace(".", "").replace(',', '.') : s.replace(",", "");
        } else if (comma >= 0) {
            s = s.replace(',', '.');
        }
        try {
            return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO.setScale(2);
        }
    }

    static String clean(String value) {
        if (value == null) {
            return null;
        }
        String s = value.replace(' ', ' ').trim().replaceAll("\\s+", " ");
        return s.isEmpty() ? null : s;
    }

    static String describe(Cell cell) {
        if (cell == null) {
            return "pusto";
        }
        return cell.getCellType() == CellType.NUMERIC ? String.valueOf(cell.getNumericCellValue()) : "tekst";
    }
}
