package pl.ngo.budget;

import org.junit.jupiter.api.Test;
import pl.ngo.budget.util.MonthlySplit;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonthlySplitTest {

    @Test
    void splitsEvenAnnualAmountAcrossEveryMonth() {
        List<BigDecimal> shares = MonthlySplit.shares(new BigDecimal("1200.00"));
        assertEquals(12, shares.size());
        for (BigDecimal share : shares) {
            assertEquals(new BigDecimal("100.00"), share);
        }
    }

    @Test
    void putsRemainderOnDecember() {
        List<BigDecimal> shares = MonthlySplit.shares(new BigDecimal("100.00"));
        for (int i = 0; i < 11; i++) {
            assertEquals(new BigDecimal("8.33"), shares.get(i));
        }
        assertEquals(new BigDecimal("8.37"), shares.get(11));
        assertEquals(new BigDecimal("100.00"), shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void splitsCoverageAcrossActiveGrantMonths() {
        List<BigDecimal> shares = MonthlySplit.sharesAcross(new BigDecimal("12000.00"), 8);
        assertEquals(8, shares.size());
        for (int i = 0; i < 7; i++) {
            assertEquals(new BigDecimal("1500.00"), shares.get(i));
        }
        assertEquals(new BigDecimal("1500.00"), shares.get(7));
        assertEquals(new BigDecimal("12000.00"), shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void splitsCoverageAcrossMonthsActiveInOneYear() {
        List<BigDecimal> shares = MonthlySplit.sharesAcross(new BigDecimal("9000.00"), 3);
        assertEquals(3, shares.size());
        assertEquals(new BigDecimal("3000.00"), shares.get(0));
        assertEquals(new BigDecimal("3000.00"), shares.get(1));
        assertEquals(new BigDecimal("3000.00"), shares.get(2));
    }

    @Test
    void monthlyAmountIsGrantTotalDividedByActiveMonths() {
        assertEquals(new BigDecimal("12727.27"),
                MonthlySplit.amountPerActiveMonth(new BigDecimal("140000.00"), 11));
        assertEquals(new BigDecimal("11666.67"),
                MonthlySplit.amountPerActiveMonth(new BigDecimal("140000.00"), 12));
        assertEquals(new BigDecimal("0.00"),
                MonthlySplit.amountPerActiveMonth(new BigDecimal("140000.00"), 0));
    }

    @Test
    void annualFromShareIsTwelveTimesTheMonth() {
        assertEquals(new BigDecimal("1200.12"), MonthlySplit.annualFromShare(new BigDecimal("100.01")));
    }

    @Test
    void recognizesSalaryAndAdminRowsOnly() {
        assertTrue(MonthlySplit.isSalaryOrAdminCategory("KOSZT_PER"));
        assertTrue(MonthlySplit.isSalaryOrAdminCategory("KOSZT_ADM"));
        assertFalse(MonthlySplit.isSalaryOrAdminCategory("KOSZT_LOG"));
        assertTrue(MonthlySplit.isSalaryOrAdminRow("wynagrodzenia"));
        assertTrue(MonthlySplit.isSalaryOrAdminRow("employee-4"));
        assertTrue(MonthlySplit.isSalaryOrAdminRow("admin-biuro"));
        assertFalse(MonthlySplit.isSalaryOrAdminRow("podroze"));
    }
}
