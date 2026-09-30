package pl.ngo.budget.entity.cost;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pl.ngo.budget.entity.coverage.BudgetItemTemplate;
import pl.ngo.budget.entity.coverage.Project;

import java.math.BigDecimal;

@Entity
@Table(name = "cost_allocations")
@Getter
@Setter
public class CostAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expenditure_id")
    private Expenditure expenditure;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private BudgetItemTemplate category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    /** Etykieta pozycji (np. Czynsz, Koszty księgowe). */
    private String label;

    /** Grupa kosztów admin: BIURO, POZOSTALE, WYNAGRODZENIA. */
    private String adminGroup;

    @Column(nullable = false)
    private Integer fiscalYear;

    /** 1–12 for monthly plan lines; null = annual plan line from budget wizard. */
    private Integer planMonth;

    /** When true, annual amount is split across months during new-budget wizard. */
    @Column(nullable = false)
    private boolean splitToMonths = false;

    @Column(nullable = false)
    private boolean active = true;
}
