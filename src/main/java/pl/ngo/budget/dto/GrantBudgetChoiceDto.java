package pl.ngo.budget.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GrantBudgetChoiceDto {

    private String value;
    private String label;

    public static GrantBudgetChoiceDto of(String value, String label) {
        GrantBudgetChoiceDto choice = new GrantBudgetChoiceDto();
        choice.setValue(value);
        choice.setLabel(label);
        return choice;
    }
}
