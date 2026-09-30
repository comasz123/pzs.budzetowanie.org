package pl.ngo.budget.entity.cost;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.ngo.budget.entity.coverage.GrantCoverage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "budget_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BudgetItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(precision = 12, scale = 2)
    private BigDecimal plannedCost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private BudgetItem parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BudgetItem> children = new ArrayList<>();

    @OneToMany(mappedBy = "budgetItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GrantCoverage> coverages = new ArrayList<>();

    public boolean isCategory() {
        return children != null && !children.isEmpty();
    }

    public BigDecimal getEffectivePlannedCost() {
        if (isCategory()) {
            return children.stream()
                    .map(BudgetItem::getEffectivePlannedCost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return plannedCost != null ? plannedCost : BigDecimal.ZERO;
    }

    public BigDecimal getTotalCovered() {
        if (isCategory()) {
            return children.stream()
                    .map(BudgetItem::getTotalCovered)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return coverages.stream()
                .map(GrantCoverage::getCoveredAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getBalance() {
        return getTotalCovered().subtract(getEffectivePlannedCost());
    }
}
