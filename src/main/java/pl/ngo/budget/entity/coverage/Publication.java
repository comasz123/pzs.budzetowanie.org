package pl.ngo.budget.entity.coverage;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "publications")
@Getter
@Setter
public class Publication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(precision = 12, scale = 2)
    private BigDecimal plannedCost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grant_id")
    private Grant grant;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * „Planowany Koszt” w budżecie: stała kwota z importu / przygotowania budżetu albo wpisana w edycji budżetu.
     * Edycja miesięcy zmienia {@code plannedCost} (podstawę miesięcy), nigdy tej kwoty.
     */
    @Column(name = "budget_planned_cost", precision = 12, scale = 2)
    private BigDecimal budgetPlannedCost;

    /** Planowany koszt; dla pozycji sprzed tej kolumny — obecna kwota. */
    public BigDecimal budgetPlannedOrCost() {
        return budgetPlannedCost != null ? budgetPlannedCost : plannedCost;
    }

    @PrePersist
    void freezeBudgetPlannedCost() {
        if (budgetPlannedCost == null) {
            budgetPlannedCost = plannedCost;
        }
    }
}
