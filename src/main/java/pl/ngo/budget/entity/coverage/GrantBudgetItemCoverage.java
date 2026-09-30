package pl.ngo.budget.entity.coverage;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "grant_budget_item_coverages")
@Getter
@Setter
public class GrantBudgetItemCoverage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grant_budget_item_id", nullable = false)
    private GrantBudgetItem grantBudgetItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrgBudgetSourceType orgSourceType;

    @Column(nullable = false)
    private Long orgSourceId;

    @Column(length = 120)
    private String orgSourceKey;

    /** Etykieta pozycji budżetu organizacji (np. PER / Jan Kowalski). */
    @Column(nullable = false)
    private String orgLabel;

    /** Kwota z pozycji grantu przeznaczona na pokrycie pozycji budżetu organizacji. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal coveredAmount;

    /**
     * Plan pokrycia na 2027. Używany, gdy grant kończy się w 2027.
     * {@link #coveredAmount} jest wtedy planem na 2026.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal coveredAmount2027;
}
