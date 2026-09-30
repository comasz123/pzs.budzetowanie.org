package pl.ngo.budget.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class NewBudgetWizardDto {

    private int fiscalYear;
    /** plan | month | review */
    private String step;
    private Integer currentMonth;
    private String currentMonthName;
    private List<NewBudgetCategoryDto> categories = new ArrayList<>();

    @Getter
    @Setter
    public static class NewBudgetCategoryDto {
        private Long templateId;
        private String name;
        private boolean newCategory;
        private boolean evenMonthlySplit;
        private List<NewBudgetLineDto> lines = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class NewBudgetLineDto {
        private Long allocationId;
        private Long employeeId;
        private String adminGroup;
        private String label;
        private BigDecimal annualAmount = BigDecimal.ZERO;
        private BigDecimal monthAmount = BigDecimal.ZERO;
        private BigDecimal remainingAmount = BigDecimal.ZERO;
        private boolean splitToMonths;
        private boolean newLine;
        private boolean newCategory;
    }
}
