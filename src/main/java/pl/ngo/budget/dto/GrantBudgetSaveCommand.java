package pl.ngo.budget.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class GrantBudgetSaveCommand {

    private BigDecimal totalAmount;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<ItemCommand> items = new ArrayList<>();
    private List<TrancheCommand> tranches = new ArrayList<>();

    @Getter
    @Setter
    public static class ItemCommand {
        private Long budgetItemId;
        /** Indeks pozycji nadrzędnej na liście items (musi być mniejszy niż indeks tej pozycji). */
        private Integer parentIndex;
        private String name;
        private String code;
        private BigDecimal plannedAmount;
        private List<CoverageCommand> coverages = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class CoverageCommand {
        private String sourceRef;
        private String orgSourceType;
        private Long orgSourceId;
        private String orgSourceKey;
        private String label;
        private BigDecimal amount;
        private BigDecimal amount2027;
    }

    @Getter
    @Setter
    public static class TrancheCommand {
        private Long id;
        private LocalDate plannedDate;
        private BigDecimal plannedAmount;
        private boolean received;
        private LocalDate receivedDate;
        private BigDecimal receivedAmount;
    }
}
