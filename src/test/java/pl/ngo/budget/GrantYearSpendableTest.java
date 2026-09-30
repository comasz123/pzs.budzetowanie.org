package pl.ngo.budget;

import org.junit.jupiter.api.Test;
import pl.ngo.budget.entity.coverage.Grant;
import pl.ngo.budget.entity.coverage.GrantTranche;
import pl.ngo.budget.util.GrantYearSpendable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GrantYearSpendableTest {

    @Test
    void grantEndingNextYearKeepsTheLaterMonthsForThatYear() {
        Grant grant = grant("2026-01-01", "2027-12-31", "240000.00");

        assertEquals(new BigDecimal("120000.00"), GrantYearSpendable.throughYear(grant, 2026));
        assertEquals(new BigDecimal("240000.00"), GrantYearSpendable.throughYear(grant, 2027));
    }

    @Test
    void grantStartedLastYearIsFullySpendableOnceItEndsThisYear() {
        Grant grant = grant("2025-05-01", "2026-03-31", "110000.00");

        assertEquals(new BigDecimal("80000.00"), GrantYearSpendable.throughYear(grant, 2025));
        assertEquals(new BigDecimal("110000.00"), GrantYearSpendable.throughYear(grant, 2026));
    }

    @Test
    void filledTranchesReplaceTheMonthSplit() {
        Grant grant = grant("2026-01-01", "2027-12-31", "240000.00");
        grant.getTranches().add(tranche("2026-03-15", "80000.00"));
        grant.getTranches().add(tranche("2027-03-15", "60000.00"));

        assertEquals(new BigDecimal("80000.00"), GrantYearSpendable.throughYear(grant, 2026));
        assertEquals(new BigDecimal("140000.00"), GrantYearSpendable.throughYear(grant, 2027));
    }

    @Test
    void earlierTrancheRollsIntoTheCurrentYear() {
        Grant grant = grant("2025-01-01", "2026-12-31", "100000.00");
        grant.getTranches().add(tranche("2025-06-01", "40000.00"));
        grant.getTranches().add(tranche("2026-06-01", "50000.00"));

        assertEquals(new BigDecimal("90000.00"), GrantYearSpendable.throughYear(grant, 2026));
        assertEquals(new BigDecimal("50000.00"), GrantYearSpendable.inYear(grant, 2026));
        assertEquals(new BigDecimal("10000.00"), GrantYearSpendable.afterYear(grant, 2026));
    }

    @Test
    void displayedYearIsOnlyThatYearsShare() {
        Grant spanning = grant("2026-01-01", "2027-12-31", "240000.00");
        assertEquals(new BigDecimal("240000.00"), GrantYearSpendable.total(spanning));
        assertEquals(new BigDecimal("120000.00"), GrantYearSpendable.inYear(spanning, 2026));
        assertEquals(new BigDecimal("120000.00"), GrantYearSpendable.afterYear(spanning, 2026));

        Grant crossing = grant("2025-05-01", "2026-03-31", "110000.00");
        assertEquals(new BigDecimal("30000.00"), GrantYearSpendable.inYear(crossing, 2026));
        assertEquals(new BigDecimal("0.00"), GrantYearSpendable.afterYear(crossing, 2026));

        Grant withTranches = grant("2026-01-01", "2027-12-31", "240000.00");
        withTranches.getTranches().add(tranche("2026-03-15", "80000.00"));
        withTranches.getTranches().add(tranche("2027-03-15", "60000.00"));
        assertEquals(new BigDecimal("80000.00"), GrantYearSpendable.inYear(withTranches, 2026));
        assertEquals(new BigDecimal("160000.00"), GrantYearSpendable.afterYear(withTranches, 2026));
    }

    @Test
    void allocationFollowsActiveMonthsAndIgnoresTranches() {
        Grant crossing = grant("2025-05-01", "2026-03-31", "110000.00");
        crossing.getTranches().add(tranche("2026-06-10", "60000.00"));

        assertEquals(new BigDecimal("30000.00"), GrantYearSpendable.allocatedByActiveMonths(crossing, 2026));
        assertEquals(new BigDecimal("60000.00"), GrantYearSpendable.inYear(crossing, 2026));

        Grant insideYear = grant("2026-02-01", "2026-11-30", "60000.00");
        insideYear.getTranches().add(tranche("2026-06-01", "50000.00"));
        assertEquals(new BigDecimal("60000.00"), GrantYearSpendable.allocatedByActiveMonths(insideYear, 2026));

        Grant nextYear = grant("2026-05-01", "2027-04-30", "150000.00");
        assertEquals(new BigDecimal("100000.00"), GrantYearSpendable.allocatedByActiveMonths(nextYear, 2026));
    }

    @Test
    void displayedTotalUsesGrantAmountUntilTheGrantRunsIntoTheNextYear() {
        Grant endingThisYear = grant("2025-05-01", "2026-03-31", "110000.00");
        Grant runningIntoNextYear = grant("2026-01-01", "2027-12-31", "240000.00");
        BigDecimal coveragePlan = new BigDecimal("80000.00");

        assertEquals(new BigDecimal("110000.00"), GrantYearSpendable.displayedTotal(endingThisYear, 2026, coveragePlan));
        assertEquals(coveragePlan, GrantYearSpendable.displayedTotal(runningIntoNextYear, 2026, coveragePlan));
        assertEquals(new BigDecimal("240000.00"), GrantYearSpendable.displayedTotal(runningIntoNextYear, 2027, coveragePlan));
    }

    private static Grant grant(String start, String end, String amount) {
        Grant grant = new Grant();
        grant.setStartDate(LocalDate.parse(start));
        grant.setEndDate(LocalDate.parse(end));
        grant.setTotalAmount(new BigDecimal(amount));
        grant.setTranches(new ArrayList<>());
        return grant;
    }

    private static GrantTranche tranche(String date, String amount) {
        GrantTranche tranche = new GrantTranche();
        tranche.setPlannedDate(LocalDate.parse(date));
        tranche.setPlannedAmount(new BigDecimal(amount));
        return tranche;
    }
}
