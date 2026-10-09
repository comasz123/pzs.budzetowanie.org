package pl.ngo.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BudgetStructureItemDto {

    private String rowKey;
    private String name;
    private String parentRowKey;
    private boolean navigable = true;
    private boolean deletable;
    private Long categoryTemplateId;
    private BigDecimal plannedAmount;
    /** Podpozycje (całe drzewo) — wypełniane przy {@code getTree}. */
    private List<BudgetStructureItemDto> children = new ArrayList<>();
}
