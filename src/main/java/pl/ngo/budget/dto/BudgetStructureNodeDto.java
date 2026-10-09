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
    /** Id kategorii głównej, gdy węzeł jest kategorią; w przeciwnym razie null. */
    private Long categoryTemplateId;
    /** Klucz nadrzędnej pozycji, gdy węzeł jest podpozycją. */
    private String parentRowKey;
    private List<BudgetStructureBreadcrumbDto> breadcrumbs = new ArrayList<>();
    private List<BudgetStructureItemDto> items = new ArrayList<>();
    /** Kategorie i podkategorie, do których można przenieść wydatek. */
    private List<BudgetStructureBreadcrumbDto> moveTargets = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetStructureBreadcrumbDto {
        private String rowKey;
        private String name;
    }
}
