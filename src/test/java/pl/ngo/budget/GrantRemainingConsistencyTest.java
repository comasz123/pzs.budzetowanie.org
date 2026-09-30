package pl.ngo.budget;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pl.ngo.budget.dto.BudgetDashboardDto;
import pl.ngo.budget.dto.GrantListRowDto;
import pl.ngo.budget.service.BudgetMatrixService;
import pl.ngo.budget.service.GrantBudgetService;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
            BigDecimal total = row.getGrant().getTotalAmount();
            BigDecimal leftToSpend = dashboardRemaining.get(grantName);
            BigDecimal yearAmount = dashboard.getGrantYearAmountByName().get(grantName);
            BigDecimal shownTotal = dashboard.getGrantTotalByName().get(grantName);
            BigDecimal allocated = dashboard.getTotalCoverageByGrant().getOrDefault(grantName, BigDecimal.ZERO);
            BigDecimal nextYear = dashboard.getGrantNextYearByName().get(grantName);
            boolean runsPastYear = row.getGrant().getEndDate() != null
                    && row.getGrant().getEndDate().getYear() > year;
            if (!runsPastYear) {
                assertEquals(0, total.compareTo(shownTotal), grantName);
            }
            assertEquals(0, shownTotal.subtract(allocated).setScale(2, RoundingMode.HALF_UP).compareTo(leftToSpend),
                    grantName);
            assertEquals(true, yearAmount.signum() >= 0, grantName);
            assertEquals(true, nextYear.signum() >= 0, grantName);
            assertEquals(true, yearAmount.add(nextYear).compareTo(total) <= 0, grantName);
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
