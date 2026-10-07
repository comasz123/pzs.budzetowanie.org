package pl.ngo.budget.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class GrantBudgetChoiceGroupDto {

    /** Null groups render as loose options, without an optgroup. */
    private String label;
    private List<GrantBudgetChoiceDto> options = new ArrayList<>();
}
