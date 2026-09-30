package pl.ngo.budget;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pl.ngo.budget.repository.GrantRepository;
import pl.ngo.budget.service.BudgetExcelExportService;
import pl.ngo.budget.service.ViewExcelExportService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ViewExcelExportTest {

    @Autowired
    private ViewExcelExportService viewExcelExportService;

    @Autowired
    private BudgetExcelExportService budgetExcelExportService;

    @Autowired
    private GrantRepository grantRepository;

    @Test
    void everyDataViewExportsAWorkbook() throws Exception {
        assertHeader(out -> viewExcelExportService.writeHome(out, 2026), "Miesiąc");
        assertHeader(out -> viewExcelExportService.writeGrants(out, 2026), "Kod");
        assertHeader(out -> viewExcelExportService.writeSponsors(out), "Nazwa");
        assertHeader(out -> viewExcelExportService.writeEmployees(out), "Imię i nazwisko");
        assertHeader(out -> viewExcelExportService.writeExpenditures(out, 2026, 1), "Nr dokumentu");
        assertHeader(out -> viewExcelExportService.writeNewBudgetPlan(out, 2027), "Kategoria");
        assertHeader(out -> viewExcelExportService.writeNewBudgetMonth(out, 2027, 1), "Kategoria");
        assertHeader(out -> viewExcelExportService.writeNewBudgetReview(out, 2026), "Pozycja");
        try (Workbook month = workbook(out -> budgetExcelExportService.writeDashboard(out, 2026, 3, false))) {
            assertEquals(1, month.getNumberOfSheets());
            assertEquals("Zostało do wydania (rok)", month.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
        }
        try (Workbook year = workbook(out -> budgetExcelExportService.writeDashboard(out, 2026, null, false))) {
            assertEquals(1, year.getNumberOfSheets(), "eksport roku to bieżąca tabela, bez arkusza wszystkich miesięcy");
        }
        assertHeader(out -> budgetExcelExportService.writeDashboard(out, 2026, null, true), "Pozycja / Koszt");
        assertHeader(out -> budgetExcelExportService.writeRow(out, 2026, null, "wynagrodzenia", false), "Zostało do wydania (rok)");

        Long grantId = grantRepository.findAll().stream().findFirst().map(grant -> grant.getId()).orElseThrow();
        try (Workbook workbook = workbook(out -> viewExcelExportService.writeGrantBudget(out, grantId))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals("Transza", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("Pozycja grantu", workbook.getSheetAt(1).getRow(0).getCell(0).getStringCellValue());
        }
    }

    private static void assertHeader(Writer writer, String expected) throws Exception {
        try (Workbook workbook = workbook(writer)) {
            assertTrue(workbook.getNumberOfSheets() > 0);
            assertEquals(expected, workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
        }
    }

    private static Workbook workbook(Writer writer) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.write(output);
        return WorkbookFactory.create(new ByteArrayInputStream(output.toByteArray()));
    }

    @FunctionalInterface
    private interface Writer {
        void write(java.io.OutputStream output) throws Exception;
    }
}
