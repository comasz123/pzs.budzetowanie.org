package pl.ngo.budget.entity.coverage;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "budget_subcategory_orders",
        uniqueConstraints = @UniqueConstraint(columnNames = {"parent_row_key", "row_key"})
)
@Getter
@Setter
public class BudgetSubcategoryOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_row_key", nullable = false, length = 120)
    private String parentRowKey;

    @Column(name = "row_key", nullable = false, length = 120)
    private String rowKey;

    @Column(nullable = false)
    private int displayOrder;

    @Column(length = 200)
    private String name;

    @Column(nullable = false)
    private boolean hidden = false;

    @Column(name = "has_subcategories", nullable = false)
    private boolean hasSubcategories = true;

    /** „Planowany Koszt” pozycji (roczny, wpisany w edycji budżetu). */
    @Column(name = "planned_amount", precision = 12, scale = 2)
    private java.math.BigDecimal plannedAmount;

    /** Roczna kwota rozpisana na miesiące (po równo); null = jak planowany koszt (dane sprzed tej kolumny). */
    @Column(name = "months_amount", precision = 12, scale = 2)
    private java.math.BigDecimal monthsAmount;

    public java.math.BigDecimal monthsOrPlanned() {
        return monthsAmount != null ? monthsAmount : plannedAmount;
    }
}
