package pl.ngo.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BudgetStructureNodeDto {

    private String rowKey;
    private String name;
    private boolean editable = true;
    private boolean evenMonthlySplit;
    private List<BudgetStructureBreadcrumbDto> breadcrumbs = new ArrayList<>();
    private List<BudgetStructureItemDto> items = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetStructureBreadcrumbDto {
        private String rowKey;
        private String name;
    }
}
