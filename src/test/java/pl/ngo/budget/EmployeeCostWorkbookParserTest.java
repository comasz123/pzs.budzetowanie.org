package pl.ngo.budget;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import pl.ngo.budget.service.EmployeeCostWorkbookParser;
import pl.ngo.budget.service.EmployeeCostWorkbookParser.ParseResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EmployeeCostWorkbookParserTest {

    private static final String[] MONTHS = {"Styczeń ", "Luty", "Marzec", "Kwiecień", "Maj", "Czerwiec",
            "Lipiec", "Sierpień", "Wrzesień", "Październik", "Listopad", "Grudzień"};

    @Test
    void parsesSyntheticWorkbook() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Koszty");
            Row header = sheet.createRow(1);
            for (int m = 0; m < 12; m++) {
                header.createCell(2 + m * 10).setCellValue(MONTHS[m]);
            }
            sheet.createRow(3).createCell(0).setCellValue("Umowy o pracę");
            person(sheet, 4, "Borowiec Beata", 100);
            sheet.createRow(5).createCell(0).setCellValue("Umowy zlecenia");
            person(sheet, 6, "ZYCH IGOR", 50);
            Row nameless = person(sheet, 7, null, 10);
            nameless.createCell(0).setCellValue(1562.27);
            sheet.createRow(8).createCell(0).setCellValue("Ekwiwalenty");
            person(sheet, 9, "Ekwiwalent b. Borowiec", 5);
            wb.write(out);
            bytes = out.toByteArray();
        }

        ParseResult r = EmployeeCostWorkbookParser.parse(new ByteArrayInputStream(bytes), Map.of());

        assertEquals(3, r.rows().size());
        var b = r.rows().get(0);
        assertEquals("Borowiec", b.lastName());
        assertEquals("Beata", b.firstName());
        assertEquals(0, b.total().compareTo(new BigDecimal("1200")));
        assertEquals("Zych", r.rows().get(1).lastName());
        assertEquals("Franke", r.rows().get(2).lastName());
        assertEquals(EmployeeCostWorkbookParser.Contract.UMOWA_ZLECENIE, r.rows().get(2).contract());
    }

    private static Row person(Sheet sheet, int rowIdx, String name, double perMonth) {
        Row row = sheet.createRow(rowIdx);
        if (name != null) {
            row.createCell(0).setCellValue(name);
        }
        row.createCell(1).setCellValue(perMonth * 12);
        for (int m = 0; m < 12; m++) {
            row.createCell(2 + m * 10).setCellValue(perMonth);
        }
        return row;
    }

    @Test
    void parsesTextNumbers() {
        assertEquals(0, EmployeeCostWorkbookParser.parseNumberText("1 038,71\u00a0").compareTo(new BigDecimal("1038.71")));
        assertEquals(0, EmployeeCostWorkbookParser.parseNumberText("1,900.71 ").compareTo(new BigDecimal("1900.71")));
    }

    /** Uruchamiany tylko gdy podano -Dbudget.xlsx=/ścieżka/do/pliku.xlsx */
    @Test
    void realWorkbookReport() throws Exception {
        String path = System.getProperty("budget.xlsx");
        assumeTrue(path != null && Files.exists(Path.of(path)));
        try (var in = Files.newInputStream(Path.of(path))) {
            ParseResult r = EmployeeCostWorkbookParser.parse(in,
                    Map.of());
            r.warnings().forEach(w -> System.out.println("WARN " + w));
            java.util.Map<String, BigDecimal> bySource = new java.util.TreeMap<>();
            BigDecimal unalloc = BigDecimal.ZERO;
            for (var x : r.rows()) {
                BigDecimal assigned = BigDecimal.ZERO;
                for (var e : x.sources().entrySet()) {
                    BigDecimal t = e.getValue().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                    bySource.merge(e.getKey(), t, BigDecimal::add);
                    assigned = assigned.add(t);
                }
                unalloc = unalloc.add(x.total().subtract(assigned));
            }
            bySource.forEach((k, v) -> System.out.println("SRC " + k + " " + v));
            System.out.println("SRC unallocated " + unalloc);
            r.rows().forEach(x -> System.out.println(x.sheetRow() + " " + x.contract() + " " + x.lastName()
                    + " | " + x.firstName() + " | " + x.total() + " vs B " + x.annualFromSheet()));
        }
    }
}
