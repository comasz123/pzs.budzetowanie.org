package pl.ngo.budget.util;

import pl.ngo.budget.entity.cost.Expenditure;
import pl.ngo.budget.entity.coverage.Grant;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public final class GrantPeriodCoverage {

    private GrantPeriodCoverage() {
    }

    /** Whether an expenditure reduces the grant budget (spent / remaining). */
    public static boolean countsTowardGrant(Expenditure expenditure) {
        if (expenditure == null || expenditure.getGrant() == null) {
            return false;
        }
        return isWithinGrantPeriod(expenditure.getGrant(), expenditure.getIssueDate());
    }

    public static boolean isWithinGrantPeriod(Grant grant, LocalDate date) {
        if (grant == null || date == null) {
            return false;
        }
        LocalDate start = grant.getStartDate();
        LocalDate end = grant.getEndDate();
        if (start == null || end == null) {
            return false;
        }
        return !date.isBefore(start) && !date.isAfter(end);
    }

    /** Whether the grant period overlaps a budget year. A reversed range is swapped. */
    public static boolean overlapsFiscalYear(LocalDate start, LocalDate end, int fiscalYear) {
        if (start == null && end == null) {
            return true;
        }
        LocalDate from = start != null ? start : end;
        LocalDate to = end != null ? end : start;
        if (to.isBefore(from)) {
            LocalDate earlier = to;
            to = from;
            from = earlier;
        }
        LocalDate yearStart = LocalDate.of(fiscalYear, 1, 1);
        LocalDate yearEnd = LocalDate.of(fiscalYear, 12, 31);
        return !from.isAfter(yearEnd) && !to.isBefore(yearStart);
    }

    /** Inclusive calendar months of the grant. A reversed range is swapped. Missing dates yield none. */
    public static List<YearMonth> activeMonths(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return List.of();
        }
        LocalDate from = start != null ? start : end;
        LocalDate to = end != null ? end : start;
        if (to.isBefore(from)) {
            LocalDate earlier = to;
            to = from;
            from = earlier;
        }
        YearMonth cursor = YearMonth.from(from);
        YearMonth last = YearMonth.from(to);
        List<YearMonth> months = new ArrayList<>();
        while (!cursor.isAfter(last)) {
            months.add(cursor);
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    public static int activeMonthsInYear(LocalDate start, LocalDate end, int year) {
        int count = 0;
        for (YearMonth month : activeMonths(start, end)) {
            if (month.getYear() == year) {
                count++;
            }
        }
        return count;
    }

    /** Grants whose duration includes the given date (inclusive). */
    public static List<Grant> grantsCoveringDate(List<Grant> grants, LocalDate date) {
        if (grants == null || date == null) {
            return List.of();
        }
        return grants.stream()
                .filter(grant -> isWithinGrantPeriod(grant, date))
                .toList();
    }
}
