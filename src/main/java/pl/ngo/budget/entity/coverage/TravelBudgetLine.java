package pl.ngo.budget.entity.coverage;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "travel_budget_lines")
@Getter
@Setter
public class TravelBudgetLine {

    public enum TravelScope {
        DOMESTIC, INTERNATIONAL
    }

    public enum ExpenseType {
        TRANSPORT, HOTEL, MEALS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TravelScope scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseType expenseType;

    @Column(precision = 12, scale = 2)
    private BigDecimal plannedCost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grant_id")
    private Grant grant;

    @Column(nullable = false)
    private boolean active = true;
}
