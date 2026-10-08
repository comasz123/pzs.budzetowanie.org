package pl.ngo.budget.util;

import pl.ngo.budget.entity.coverage.Grant;
import pl.ngo.budget.entity.coverage.GrantBudgetItemCoverage;

import java.math.BigDecimal;

/**
 * Grant kończący się w 2027 ma osobny plan pokrycia na 2026 i na 2027.
 * Pozostałe granty mają jedną kwotę, używaną w każdym roku aktywności.
 */
public final class GrantCoverageYear {

    private GrantCoverageYear() {
    }

    public static boolean splits(Grant grant) {
        return grant != null && grant.getEndDate() != null && grant.getEndDate().getYear() == 2027;
    }

    public static BigDecimal amount(Grant grant, GrantBudgetItemCoverage coverage, int fiscalYear) {
        if (coverage == null) {
            return null;
        }
        if (coverage.hasMonthlyAmounts()) {
            return coverage.monthlyAmount(fiscalYear, null);
        }
        if (!splits(grant)) {
            return coverage.getCoveredAmount();
        }
        if (fiscalYear == 2027) {
            return coverage.getCoveredAmount2027();
        }
        if (fiscalYear == 2026) {
            return coverage.getCoveredAmount();
        }
        return null;
    }
}
