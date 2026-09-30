package pl.ngo.budget.service;

import org.junit.jupiter.api.Test;
import pl.ngo.budget.dto.BudgetDashboardDto.BudgetItemRowDto;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SalaryRemainderTest {

    @Test
    void remainderIsFullSalaryMinusVisibleTransfers() {
        BudgetItemRowDto employee = row("employee-1", "144000.00");
        BudgetItemRowDto remainder = row("pensja-pozostala-1", "127000.00");
        remainder.setCoverageByGrant(Map.of("Grant A", new BigDecimal("1000.00")));
        BudgetItemRowDto admin = row("pensja-admin-1", "5000.00");
        employee.setChildren(List.of(remainder, admin));

        BudgetMatrixService.rebalanceSalaryRemainders(List.of(employee));

        assertEquals(new BigDecimal("139000.00"), remainder.getTotalCost());
        assertEquals(new BigDecimal("138000.00"), remainder.getBalance());
        assertEquals(new BigDecimal("5000.00"), admin.getTotalCost());
        assertEquals(new BigDecimal("144000.00"), employee.getTotalCost());
    }

    @Test
    void remainderSubtractsAdminAndPromotionLines() {
        BudgetItemRowDto employee = row("employee-2", "96000.00");
        BudgetItemRowDto remainder = row("pensja-pozostala-2", "0.00");
        BudgetItemRowDto admin = row("pensja-admin-2", "4000.00");
        BudgetItemRowDto promotion = row("pensja-promocja-2", "2000.00");
        employee.setChildren(new java.util.ArrayList<>(List.of(remainder, admin, promotion)));

        BudgetMatrixService.rebalanceSalaryRemainders(List.of(category("wynagrodzenia", employee)));

        assertEquals(new BigDecimal("90000.00"), remainder.getTotalCost());
    }

    private static BudgetItemRowDto category(String key, BudgetItemRowDto child) {
        BudgetItemRowDto row = row(key, "0.00");
        row.setChildren(List.of(child));
        return row;
    }

    private static BudgetItemRowDto row(String key, String amount) {
        BudgetItemRowDto row = new BudgetItemRowDto();
        row.setRowKey(key);
        row.setTotalCost(new BigDecimal(amount));
        row.setCoverageByGrant(new LinkedHashMap<>());
        return row;
    }
}
