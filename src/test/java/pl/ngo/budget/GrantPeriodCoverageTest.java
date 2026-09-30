package pl.ngo.budget;

import org.junit.jupiter.api.Test;
import pl.ngo.budget.entity.cost.Expenditure;
import pl.ngo.budget.entity.coverage.Grant;
import pl.ngo.budget.util.GrantPeriodCoverage;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrantPeriodCoverageTest {

    @Test
    void countsExpenditureOnGrantBoundaries() {
        Grant grant = grant(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 10, 31));
        assertTrue(GrantPeriodCoverage.countsTowardGrant(expenditure(grant, LocalDate.of(2026, 3, 1))));
        assertTrue(GrantPeriodCoverage.countsTowardGrant(expenditure(grant, LocalDate.of(2026, 10, 31))));
    }

    @Test
    void mayFirstIsWithinKulturaGrantPeriod() {
        Grant kultura = grant(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 9, 30));
        assertTrue(GrantPeriodCoverage.isWithinGrantPeriod(kultura, LocalDate.of(2026, 5, 1)));
        assertFalse(GrantPeriodCoverage.isWithinGrantPeriod(kultura, LocalDate.of(2026, 4, 30)));
    }

    @Test
    void ignoresExpenditureOutsideGrantPeriod() {
        Grant grant = grant(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertFalse(GrantPeriodCoverage.countsTowardGrant(expenditure(grant, LocalDate.of(2025, 12, 31))));
        assertFalse(GrantPeriodCoverage.countsTowardGrant(expenditure(grant, LocalDate.of(2027, 1, 1))));
    }

    @Test
    void overlapsTheBudgetYearTheGrantRunsIn() {
        assertTrue(GrantPeriodCoverage.overlapsFiscalYear(
                LocalDate.of(2025, 5, 1), LocalDate.of(2025, 12, 31), 2025));
        assertFalse(GrantPeriodCoverage.overlapsFiscalYear(
                LocalDate.of(2025, 5, 1), LocalDate.of(2025, 12, 31), 2026));
        assertTrue(GrantPeriodCoverage.overlapsFiscalYear(
                LocalDate.of(2025, 5, 1), LocalDate.of(2025, 3, 31), 2025));
    }

    @Test
    void countsActiveMonthsInsideOneYear() {
        assertEquals(8, GrantPeriodCoverage.activeMonthsInYear(
                LocalDate.of(2025, 5, 1), LocalDate.of(2025, 12, 31), 2025));
        assertEquals(0, GrantPeriodCoverage.activeMonthsInYear(
                LocalDate.of(2025, 5, 1), LocalDate.of(2025, 12, 31), 2026));
    }

    @Test
    void countsActiveMonthsAcrossYearsAndSwapsReversedRange() {
        assertEquals(List.of(
                YearMonth.of(2025, 5),
                YearMonth.of(2025, 6),
                YearMonth.of(2025, 7),
                YearMonth.of(2025, 8),
                YearMonth.of(2025, 9),
                YearMonth.of(2025, 10),
                YearMonth.of(2025, 11),
                YearMonth.of(2025, 12),
                YearMonth.of(2026, 1),
                YearMonth.of(2026, 2),
                YearMonth.of(2026, 3)
        ), GrantPeriodCoverage.activeMonths(LocalDate.of(2026, 3, 31), LocalDate.of(2025, 5, 1)));
        assertEquals(8, GrantPeriodCoverage.activeMonthsInYear(
                LocalDate.of(2026, 3, 31), LocalDate.of(2025, 5, 1), 2025));
        assertEquals(3, GrantPeriodCoverage.activeMonthsInYear(
                LocalDate.of(2026, 3, 31), LocalDate.of(2025, 5, 1), 2026));
    }

    @Test
    void ignoresWhenGrantHasNoPeriod() {
        Grant grant = grant(null, LocalDate.of(2026, 12, 31));
        assertFalse(GrantPeriodCoverage.countsTowardGrant(expenditure(grant, LocalDate.of(2026, 6, 1))));
    }

    private static Grant grant(LocalDate start, LocalDate end) {
        Grant grant = new Grant();
        grant.setCode("G-TEST");
        grant.setStartDate(start);
        grant.setEndDate(end);
        return grant;
    }

    private static Expenditure expenditure(Grant grant, LocalDate issueDate) {
        Expenditure expenditure = new Expenditure();
        expenditure.setGrant(grant);
        expenditure.setIssueDate(issueDate);
        return expenditure;
    }
}
