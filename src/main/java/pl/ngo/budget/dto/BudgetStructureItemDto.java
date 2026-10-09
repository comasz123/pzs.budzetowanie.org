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
    /** Nazwę można zmienić (kategoria albo podpozycja; nie linie alokacji kosztów). */
    private boolean renamable;
    /** Linia wydatku (z alokacji kosztów), nie podkategoria. */
    private boolean expense;
    /** Klucz naturalny linii wydatku — do przenoszenia między kategoriami. */
    private String lineKey;
    /**
     * Rodzaj pozycji dla planowanego kosztu i rozpisu na 12 miesięcy: allocation, publication, travel, event,
     * subcategory (pusta podkategoria); null = kwota liczona z zawartości.
     */
    private String editKind;
    /** Identyfikatory pozycji danego rodzaju (dla allocation — roczne linie planu). */
    private String allocationIds;
    /** Linia pensji: bez usuwania i przenoszenia (pracowników prowadzi zakładka Pracownicy). */
    private boolean personnel;
    /** Wiersz pracownika: id i okres pracy w budżecie (od–do) dla rozpisu pensji. */
    private Long employeeId;
    private java.time.LocalDate periodFrom;
    private java.time.LocalDate periodTo;
    private BigDecimal plannedAmount;
    /** Podpozycje (całe drzewo) — wypełniane przy {@code getTree}. */
    private List<BudgetStructureItemDto> children = new ArrayList<>();
}
