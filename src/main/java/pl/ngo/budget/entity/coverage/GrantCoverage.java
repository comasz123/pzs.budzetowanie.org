package pl.ngo.budget.entity.coverage;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.ngo.budget.entity.cost.BudgetItem;

import java.math.BigDecimal;

@Entity
@Table(name = "grant_coverages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GrantCoverage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grant_id", nullable = false)
    private Grant grant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_item_id", nullable = false)
    private BudgetItem budgetItem;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal coveredAmount;
}
