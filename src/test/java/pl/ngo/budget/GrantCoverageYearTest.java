package pl.ngo.budget;

import org.junit.jupiter.api.Test;
import pl.ngo.budget.entity.coverage.Grant;
import pl.ngo.budget.entity.coverage.GrantBudgetItemCoverage;
import pl.ngo.budget.util.GrantCoverageYear;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrantCoverageYearTest {

    @Test
    void grantEndingIn2027UsesASeparateAmountPerYear() {
        Grant grant = grant("2026-05-01", "2027-03-31");
        GrantBudgetItemCoverage coverage = new GrantBudgetItemCoverage();
        coverage.setCoveredAmount(new BigDecimal("15000.00"));
        coverage.setCoveredAmount2027(new BigDecimal("8000.00"));

        assertTrue(GrantCoverageYear.splits(grant));
        assertEquals(new BigDecimal("15000.00"), GrantCoverageYear.amount(grant, coverage, 2026));
        assertEquals(new BigDecimal("8000.00"), GrantCoverageYear.amount(grant, coverage, 2027));
        assertNull(GrantCoverageYear.amount(grant, coverage, 2025));
    }

    @Test
    void otherGrantsKeepOneCoverageAmount() {
        Grant grant = grant("2026-01-01", "2026-12-31");
        GrantBudgetItemCoverage coverage = new GrantBudgetItemCoverage();
        coverage.setCoveredAmount(new BigDecimal("9000.00"));
        coverage.setCoveredAmount2027(new BigDecimal("1000.00"));

        assertFalse(GrantCoverageYear.splits(grant));
        assertEquals(new BigDecimal("9000.00"), GrantCoverageYear.amount(grant, coverage, 2026));
        assertEquals(new BigDecimal("9000.00"), GrantCoverageYear.amount(grant, coverage, 2025));
    }

    private static Grant grant(String start, String end) {
        Grant grant = new Grant();
        grant.setStartDate(LocalDate.parse(start));
        grant.setEndDate(LocalDate.parse(end));
        return grant;
    }
}
