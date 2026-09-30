package pl.ngo.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import pl.ngo.budget.dto.BudgetDashboardDto.BudgetItemRowDto;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class BudgetRowDetailDto {

    private BudgetDashboardDto dashboard;
    private BudgetItemRowDto row;
    private List<BudgetItemRowDto> breadcrumbs = new ArrayList<>();
}
