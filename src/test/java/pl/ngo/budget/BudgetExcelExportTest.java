package pl.ngo.budget;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pl.ngo.budget.dto.BudgetDashboardDto;
import pl.ngo.budget.service.BudgetExcelExportService;
import pl.ngo.budget.service.BudgetMatrixService;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class BudgetExcelExportTest {

    @Autowired
    private BudgetExcelExportService budgetExcelExportService;

    @Autowired
    private BudgetMatrixService budgetMatrixService;

    @Test
    void expandedWorkbookMatchesDashboardTotals() throws Exception {
        List<Integer> years = new ArrayList<>(budgetMatrixService.getAvailableFiscalYears());
        if (!years.contains(2026)) {
            years.add(2026);
        }
        Path output = Path.of("target/budzet-rozwiniety.xlsx");
        try (OutputStream out = Files.newOutputStream(output)) {
            budgetExcelExportService.writeYears(out, years);
        }

        try (Workbook workbook = WorkbookFactory.create(output.toFile())) {
            for (int year : years) {
                BudgetDashboardDto dashboard = budgetMatrixService.getBudgetDashboardDataForYear(year);
                Sheet annual = workbook.getSheet("Budżet " + year);
                Sheet monthly = workbook.getSheet("Miesiące " + year);
                assertNotNull(annual, "Brak arkusza roku " + year);
                assertNotNull(monthly, "Brak arkusza miesięcy " + year);
                assertEquals(0, findAmount(annual, "Razem (plan)", 1).compareTo(scale(dashboard.getTotalCost())),
                        "suma roku " + year);
                assertEquals(0, findAmount(monthly, "Razem (plan)", 13).compareTo(scale(dashboard.getTotalCost())),
                        "suma miesięcy " + year);
                assertTrue(annual.getPhysicalNumberOfRows() > dashboard.getRows().size(),
                        "arkusz roku ma zawierać pozycje rozwinięte");
            }
        }
    }

    private static BigDecimal findAmount(Sheet sheet, String label, int column) {
        for (Row row : sheet) {
            if (row.getCell(0) != null && label.equals(row.getCell(0).getStringCellValue())) {
                return BigDecimal.valueOf(row.getCell(column).getNumericCellValue()).setScale(2, java.math.RoundingMode.HALF_UP);
            }
        }
        throw new AssertionError("Nie znaleziono wiersza " + label);
    }

    private static BigDecimal scale(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
