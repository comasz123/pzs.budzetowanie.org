package pl.ngo.budget.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class MonthlySplit {

    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private MonthlySplit() {
    }

    public static boolean isSalaryOrAdminCategory(String code) {
        return "KOSZT_PER".equals(code) || "KOSZT_ADM".equals(code);
    }

    public static boolean isSalaryOrAdminRow(String rowKey) {
        if (rowKey == null || rowKey.isBlank()) {
            return false;
        }
        return "wynagrodzenia".equals(rowKey)
                || rowKey.startsWith("employee-")
                || "koszty-administracyjne".equals(rowKey)
                || rowKey.startsWith("admin-");
    }

    public static BigDecimal shareForMonth(BigDecimal annual, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Miesiąc poza zakresem 1–12");
        }
        return shares(annual).get(month - 1);
    }

    public static BigDecimal annualFromShare(BigDecimal monthlyShare) {
        return scale(monthlyShare).multiply(TWELVE).setScale(2, RoundingMode.HALF_UP);
    }

    public static List<BigDecimal> shares(BigDecimal annual) {
        return sharesAcross(annual, 12);
    }

    /** Equal parts of {@code total}; the last part keeps the remainder so the parts sum to the total. */
    public static List<BigDecimal> sharesAcross(BigDecimal totalAmount, int parts) {
        if (parts < 1) {
            throw new IllegalArgumentException("Liczba części musi być dodatnia");
        }
        BigDecimal total = scale(totalAmount);
        BigDecimal base = total.divide(BigDecimal.valueOf(parts), 2, RoundingMode.DOWN);
        List<BigDecimal> result = new ArrayList<>(parts);
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < parts - 1; i++) {
            result.add(base);
            assigned = assigned.add(base);
        }
        result.add(total.subtract(assigned).setScale(2, RoundingMode.HALF_UP));
        return result;
    }

    /** Kwota grantu / liczba miesięcy, w których grant jest aktywny. */
    public static BigDecimal amountPerActiveMonth(BigDecimal total, int activeMonths) {
        if (activeMonths <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return scale(total).divide(BigDecimal.valueOf(activeMonths), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
