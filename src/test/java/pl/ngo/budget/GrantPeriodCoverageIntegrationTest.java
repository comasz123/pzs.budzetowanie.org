package pl.ngo.budget;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pl.ngo.budget.entity.cost.Expenditure;
import pl.ngo.budget.repository.ExpenditureRepository;
import pl.ngo.budget.service.GrantBudgetService;
import pl.ngo.budget.util.GrantPeriodCoverage;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class GrantPeriodCoverageIntegrationTest {

    @Autowired
    private ExpenditureRepository expenditureRepository;

    @Autowired
    private GrantBudgetService grantBudgetService;

    @Test
    void spentTotalsExcludeExpendituresOutsideGrantPeriod() {
        List<Expenditure> expenditures = expenditureRepository.findAllWithGrantAndItemByFiscalYear(2026);
        if (expenditures.isEmpty()) {
            return;
        }

        long outsidePeriod = expenditures.stream()
                .filter(e -> !GrantPeriodCoverage.countsTowardGrant(e))
                .count();

        assertEquals(0, outsidePeriod, "Po korekcie seed wszystkie wydatki powinny mieć grant zgodny z datą");

        BigDecimal manualEligibleSum = expenditures.stream()
                .filter(GrantPeriodCoverage::countsTowardGrant)
                .map(e -> e.getGrossAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal reportedSpent = grantBudgetService.buildGrantListRows(2026).stream()
                .map(row -> row.getSpentInYear())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, manualEligibleSum.compareTo(reportedSpent),
                "Wydane w grantach musi sumować tylko wydatki w okresie trwania");
    }
}
