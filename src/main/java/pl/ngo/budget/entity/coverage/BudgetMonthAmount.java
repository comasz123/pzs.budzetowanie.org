package pl.ngo.budget.entity.coverage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Kwota pozycji budżetu w jednym miesiącu dla pozycji bez własnego planu miesięcznego (publikacja, linia podróży,
 * pusta podkategoria). Gdy pozycja ma zapisy w danym roku, ma je dla wszystkich 12 miesięcy; bez zapisów
 * jej kwota roczna dzieli się po równo.
 */
@Entity
@Table(name = "budget_month_amounts", uniqueConstraints = @UniqueConstraint(
        columnNames = {"fiscal_year", "kind", "ref_key", "plan_month"}))
@Getter
@Setter
public class BudgetMonthAmount {

    public static final String PUBLICATION = "PUBLICATION";
    public static final String TRAVEL = "TRAVEL";
    public static final String SUBCATEGORY = "SUBCATEGORY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(nullable = false, length = 20)
    private String kind;

    /** Id publikacji / linii podróży albo „rodzic|wiersz” podkategorii. */
    @Column(name = "ref_key", nullable = false, length = 240)
    private String refKey;

    @Column(name = "plan_month", nullable = false)
    private Integer planMonth;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    public static String key(String kind, String refKey, int month) {
        return kind + "|" + refKey + "|" + month;
    }
}
