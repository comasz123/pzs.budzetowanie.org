package pl.ngo.budget;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pl.ngo.budget.dto.BudgetDashboardDto;
import pl.ngo.budget.dto.GrantListRowDto;
import pl.ngo.budget.service.BudgetMatrixService;
import pl.ngo.budget.service.GrantBudgetService;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class GrantRemainingConsistencyTest {

    @Autowired
    private BudgetMatrixService budgetMatrixService;

    @Autowired
    private GrantBudgetService grantBudgetService;

    @Test
    void dashboardGrantRemainingMatchesAdminGrantsList() {
        int year = 2026;
        BudgetDashboardDto dashboard = budgetMatrixService.getBudgetDashboardDataForYear(year);
        Map<String, BigDecimal> dashboardRemaining = dashboard.getGrantRemainingByName();

        for (GrantListRowDto row : grantBudgetService.buildGrantListRows(year)) {
            String grantName = row.getGrant().getName();
            assertEquals(0, row.getRemaining().compareTo(dashboardRemaining.get(grantName)), grantName);
            assertEquals(0, row.getAllocatedInYear()
                    .compareTo(dashboard.getGrantYearAmountByName().get(grantName)), grantName);
            assertEquals(0, row.getAllocatedNextYear()
                    .compareTo(dashboard.getGrantNextYearByName().get(grantName)), grantName);
            assertEquals(0, row.getGrant().getTotalAmount()
                    .compareTo(dashboard.getGrantFullTotalByName().get(grantName)), grantName);
        }
    }

    @Test
    void grantRemainingTotalEqualsSumOfGrantColumns() {
        BudgetDashboardDto dashboard = budgetMatrixService.getBudgetDashboardDataForYear(2026);
        BigDecimal sum = dashboard.getGrantRemainingByName().values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sum.compareTo(dashboard.getGrantRemainingTotal()));
    }

    @Test
    void footerTotalsMatchKpiCards() {
        BudgetDashboardDto dashboard = budgetMatrixService.getBudgetDashboardDataForYear(2026);
        BigDecimal grantSum = dashboard.getTotalCoverageByGrant().values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, grantSum.compareTo(dashboard.getTotalGrantCoverage()));
        assertEquals(0, dashboard.getTotalCost().subtract(grantSum).compareTo(dashboard.getBalance()));
    }
}
