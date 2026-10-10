package pl.ngo.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BudgetDashboardDto {

    private BigDecimal totalCost = BigDecimal.ZERO;
    /** Suma kwot „Planowany Koszt” ze struktury budżetu (tylko widok roczny planu). */
    private BigDecimal totalPlannedCost;
    /** Planowany koszt razem minus suma miesięcy razem. */
    private BigDecimal totalMonthsGap;
    private BigDecimal totalGrantCoverage = BigDecimal.ZERO;
    private BigDecimal balance = BigDecimal.ZERO;

    /** Bilans do wyświetlania: pokrycie minus wydatek (odwrotność {@code balance} = wydatek minus pokrycie). */
    public BigDecimal getBilans() {
        return balance == null ? null : balance.negate();
    }
    private List<String> grantNames = new ArrayList<>();
    /** Budżet grantu albo plan pokrycia na rok minus pokrycie wpisane w pozycje budżetu. */
    private Map<String, BigDecimal> grantRemainingByName = new HashMap<>();
    /** Kwota zaplanowana na oglądany rok. */
    private Map<String, BigDecimal> grantYearAmountByName = new HashMap<>();
    /** Full grant amount, shown after expanding the remaining row. */
    private Map<String, BigDecimal> grantTotalByName = new HashMap<>();
    /** Kwota grantu z umowy (całość, niezależnie od roku) — wiersz „Całkowita suma grantu”. */
    private Map<String, BigDecimal> grantFullTotalByName = new HashMap<>();
    /** Grant amount left for years after the displayed budget year. */
    private Map<String, BigDecimal> grantNextYearByName = new HashMap<>();
    /** Sum of {@link #grantRemainingByName}. */
    private BigDecimal grantRemainingTotal = BigDecimal.ZERO;
    /** Planned grant coverage per grant — sum of top-level category rows. */
    private Map<String, BigDecimal> totalCoverageByGrant = new HashMap<>();
    private List<BudgetItemRowDto> rows = new ArrayList<>();
    /** Flattened rows for dashboard table (categories + subcategories + line items). */
    private List<BudgetDisplayRowDto> displayRows = new ArrayList<>();
    /** Top-level category reorder controls keyed by dashboard rowKey. */
    private Map<String, CategoryOrderInfo> categoryOrderByRowKey = new HashMap<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryOrderInfo {
        private Long categoryId;
        private boolean canMoveUp;
        private boolean canMoveDown;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetDisplayRowDto {
        private int depth;
        private String itemName;
        private String rowKey;
        private boolean linkable;
        private BigDecimal totalCost = BigDecimal.ZERO;
        private BigDecimal balance = BigDecimal.ZERO;

        /** Bilans do wyświetlania: pokrycie minus wydatek (odwrotność {@code balance} = wydatek minus pokrycie). */
        public BigDecimal getBilans() {
            return balance == null ? null : balance.negate();
        }
        private Map<String, BigDecimal> coverageByGrant = new HashMap<>();
        private Long categoryTemplateId;
        private String parentRowKey;
        /** Top-level category rowKey for collapse/expand grouping (e.g. wynagrodzenia). */
        private String categoryRootRowKey;
        private boolean draggable;
        /** Leaf planned-cost cell that month edit can save. */
        private boolean amountEditable;
        private String amountEditKind;
        private String amountEditIds;
        /** Klucz linii wydatku (wiersze alokacji, które nie mają rowKey). */
        private String lineKey;
        /** Kwota „Planowany Koszt” wpisana w strukturze budżetu. */
        private BigDecimal plannedCost;
        /** Suma kosztu z miesięcy (dla linii wydatków różna od kwoty rocznej w {@code totalCost}). */
        private BigDecimal monthsCost;
        /** „Pokrycie w miesiącach”: planowany koszt minus suma miesięcy; zero = całość rozpisana. */
        private BigDecimal monthsGap;
        /** Edycja miesiąca: część planowanego kosztu pozycji jeszcze nierozpisana na miesiące (jak monthsGap roku). */
        private BigDecimal unsplitAmount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetItemRowDto {
        private String category;
        private String itemName;
        private String rowKey;
        private boolean expandable;
        private BigDecimal totalCost = BigDecimal.ZERO;
        private BigDecimal overallCoverage = BigDecimal.ZERO;
        private BigDecimal balance = BigDecimal.ZERO;

        /** Bilans do wyświetlania: pokrycie minus wydatek (odwrotność {@code balance} = wydatek minus pokrycie). */
        public BigDecimal getBilans() {
            return balance == null ? null : balance.negate();
        }
        private Map<String, BigDecimal> coverageByGrant = new HashMap<>();
        private List<BudgetItemRowDto> children = new ArrayList<>();
        private List<CostAllocationDto> allocations = new ArrayList<>();
        /** Suma wiersza = podpozycje plus własne linie wydatków (wiersz z liniami dostał podkategorię). */
        private boolean ownAllocationsCounted;
    }
}
