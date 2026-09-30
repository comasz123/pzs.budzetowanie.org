package pl.ngo.budget.entity.cost;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import pl.ngo.budget.entity.coverage.Grant;
import pl.ngo.budget.entity.coverage.GrantBudgetItem;

import java.math.BigDecimal;

@Entity
@Table(name = "expenditure_grant_shares")
@Getter
@Setter
public class ExpenditureGrantShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expenditure_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Expenditure expenditure;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grant_id", nullable = false)
    private Grant grant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grant_budget_item_id", nullable = false)
    private GrantBudgetItem budgetItem;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "share_position", nullable = false)
    private int sharePosition;
}
