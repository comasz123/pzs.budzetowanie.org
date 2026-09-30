package pl.ngo.budget.entity.cost;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.ngo.budget.entity.coverage.Grant;

import java.math.BigDecimal;

@Entity
@Table(name = "expenditure_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExpenditureAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expenditure_id", nullable = false)
    private Expenditure expenditure;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grant_id", nullable = false)
    private Grant grant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_item_id", nullable = false)
    private BudgetItem budgetItem;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
}
