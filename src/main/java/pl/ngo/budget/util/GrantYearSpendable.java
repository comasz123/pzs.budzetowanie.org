package pl.ngo.budget.util;

import pl.ngo.budget.entity.coverage.Grant;
import pl.ngo.budget.entity.coverage.GrantTranche;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Kwota grantu, którą można wydać do końca danego roku.
 * Wypełnione transze z datą w tym roku lub wcześniej wchodzą w całości.
 * Późniejsze transze zostają na kolejne lata.
 * Bez transz kwota dzieli się według miesięcy aktywności grantu.
 */
public final class GrantYearSpendable {

    private GrantYearSpendable() {
    }

    public static BigDecimal total(Grant grant) {
        if (grant == null || grant.getTotalAmount() == null) {
            return zero();
        }
        return grant.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Grant kończący się w oglądanym roku albo wcześniej: kwota grantu.
     * Grant trwający jeszcze w kolejnym roku: suma planu pokrycia.
     */
    public static BigDecimal displayedTotal(Grant grant, int fiscalYear, BigDecimal coveragePlanSum) {
        if (grant != null && grant.getEndDate() != null && grant.getEndDate().getYear() > fiscalYear) {
            if (coveragePlanSum == null) {
                return zero();
            }
            return coveragePlanSum.setScale(2, RoundingMode.HALF_UP);
        }
        return total(grant);
    }

    /** Kwota zaplanowana do wydania w jednym roku, bez lat wcześniejszych i późniejszych. */
    public static BigDecimal inYear(Grant grant, int fiscalYear) {
        if (grant == null) {
            return zero();
        }
        BigDecimal fromTranches = trancheAmount(grant, fiscalYear, false);
        if (fromTranches != null) {
            return fromTranches;
        }
        return monthAmount(grant, fiscalYear, false);
    }

    /**
     * Część kwoty grantu przypadająca na dany rok według miesięcy aktywności.
     * Transze są wpływem gotówki i nie zmieniają tej alokacji.
     */
    public static BigDecimal allocatedByActiveMonths(Grant grant, int fiscalYear) {
        if (grant == null) {
            return zero();
        }
        return monthAmount(grant, fiscalYear, false);
    }

    /** Kwota grantu po odjęciu części z lat do bieżącego włącznie. */
    public static BigDecimal afterYear(Grant grant, int fiscalYear) {
        BigDecimal rest = total(grant).subtract(throughYear(grant, fiscalYear));
        if (rest.signum() < 0) {
            return zero();
        }
        return rest.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal throughYear(Grant grant, int fiscalYear) {
        if (grant == null) {
            return zero();
        }
        BigDecimal fromTranches = trancheAmount(grant, fiscalYear, true);
        if (fromTranches != null) {
            return fromTranches;
        }
        return monthAmount(grant, fiscalYear, true);
    }

    /**
     * @param cumulative true = lata do fiscalYear włącznie, false = tylko ten rok
     */
    private static BigDecimal trancheAmount(Grant grant, int fiscalYear, boolean cumulative) {
        List<GrantTranche> tranches = grant.getTranches();
        if (tranches == null || tranches.isEmpty()) {
            return null;
        }
        boolean anyAmount = false;
        BigDecimal sum = BigDecimal.ZERO;
        int fallbackYear = grant.getStartDate() != null ? grant.getStartDate().getYear() : fiscalYear;
        for (GrantTranche tranche : tranches) {
            BigDecimal amount = tranche.getPlannedAmount();
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            anyAmount = true;
            LocalDate date = tranche.getPlannedDate() != null ? tranche.getPlannedDate() : tranche.getReceivedDate();
            int year = date != null ? date.getYear() : fallbackYear;
            if (cumulative ? year <= fiscalYear : year == fiscalYear) {
                sum = sum.add(amount);
            }
        }
        if (!anyAmount) {
            return null;
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal monthAmount(Grant grant, int fiscalYear, boolean cumulative) {
        BigDecimal total = total(grant);
        List<YearMonth> months = GrantPeriodCoverage.activeMonths(grant.getStartDate(), grant.getEndDate());
        if (months.isEmpty()) {
            return total;
        }
        int matched = 0;
        for (YearMonth month : months) {
            if (cumulative ? month.getYear() <= fiscalYear : month.getYear() == fiscalYear) {
                matched++;
            }
        }
        if (matched <= 0) {
            return zero();
        }
        if (matched >= months.size()) {
            return total;
        }
        return total.multiply(BigDecimal.valueOf(matched))
                .divide(BigDecimal.valueOf(months.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
