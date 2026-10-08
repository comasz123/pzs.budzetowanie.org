package pl.ngo.budget.entity.coverage;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

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

    /**
     * Rzeczywiste kwoty pokrycia w miesiącach (klucz rrrrmm, np. 202603). Gdy są ustawione, mają
     * pierwszeństwo przed równym podziałem {@link #coveredAmount} na miesiące aktywności grantu.
     */
    @ElementCollection
    @CollectionTable(name = "grant_coverage_months", joinColumns = @JoinColumn(name = "coverage_id"))
    @MapKeyColumn(name = "period_key")
    @Column(name = "amount", precision = 12, scale = 2)
    @BatchSize(size = 200)
    private Map<Integer, BigDecimal> monthlyAmounts = new HashMap<>();

    public static int periodKey(int year, int month) {
        return year * 100 + month;
    }

    public boolean hasMonthlyAmounts() {
        return monthlyAmounts != null && !monthlyAmounts.isEmpty();
    }

    /** Kwota w danym miesiącu; dla {@code month == null} suma miesięcy danego roku. */
    public BigDecimal monthlyAmount(int year, Integer month) {
        if (month != null) {
            return monthlyAmounts.get(periodKey(year, month));
        }
        return monthlyAmounts.entrySet().stream()
                .filter(e -> e.getKey() / 100 == year)
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
