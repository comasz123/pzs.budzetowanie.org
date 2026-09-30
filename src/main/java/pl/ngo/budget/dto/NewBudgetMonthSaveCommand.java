package pl.ngo.budget.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class NewBudgetMonthSaveCommand {

    private List<CategoryCommand> categories = new ArrayList<>();

    @Getter
    @Setter
    public static class CategoryCommand {
        private List<LineCommand> lines = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class LineCommand {
        private Long annualAllocationId;
        private BigDecimal monthAmount;
    }
}
