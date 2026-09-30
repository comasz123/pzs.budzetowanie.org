package pl.ngo.budget.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class NewBudgetPlanSaveCommand {

    private List<CategoryCommand> categories = new ArrayList<>();

    @Getter
    @Setter
    public static class CategoryCommand {
        private Long templateId;
        private String name;
        private boolean newCategory;
        private List<LineCommand> lines = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class LineCommand {
        private Long allocationId;
        private String label;
        private Long employeeId;
        private String adminGroup;
        private BigDecimal annualAmount;
        private boolean splitToMonths;
        private boolean newLine;
    }
}
