package pl.ngo.budget.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class GrantSaveCommand {

    private Long id;
    private String code;
    private String name;
    private Long sponsorId;
    private Long projectId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalAmount;
    private String currency;
    private Long primarySponsorContactId;
    private Long financialSponsorContactId;
    private Long grantCoordinatorId;
    private boolean active = true;
    private List<BudgetItemCommand> budgetItems = new ArrayList<>();
    private List<TrancheCommand> tranches = new ArrayList<>();

    @Getter
    @Setter
    public static class BudgetItemCommand {
        private Long templateId;
        private boolean newTemplate;
        private String code;
        private String name;
        private BigDecimal plannedAmount;
    }

    @Getter
    @Setter
    public static class TrancheCommand {
        private Long id;
        private Integer trancheNumber;
        private LocalDate plannedDate;
        private BigDecimal plannedAmount;
        private boolean received;
        private LocalDate receivedDate;
        private BigDecimal receivedAmount;
    }
}
