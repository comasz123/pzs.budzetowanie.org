package pl.ngo.budget.entity.cost;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.ngo.budget.entity.coverage.Grant;
import pl.ngo.budget.entity.coverage.GrantBudgetItem;
import pl.ngo.budget.entity.coverage.Project;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expenditures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Expenditure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String documentNumber;

    @Column(nullable = false)
    private LocalDate issueDate;

    private LocalDate paymentDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal vatAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grant_id", nullable = false)
    private Grant grant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grant_budget_item_id", nullable = false)
    private GrantBudgetItem budgetItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false)
    private Integer fiscalYear;

    @OneToMany(mappedBy = "expenditure", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CostAllocation> costAllocations = new ArrayList<>();

    @OneToMany(mappedBy = "expenditure", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sharePosition")
    private List<ExpenditureGrantShare> grantShares = new ArrayList<>();

    @PrePersist
    @PreUpdate
    public void calculateGrossAmount() {
        if (netAmount != null) {
            BigDecimal vat = (vatAmount != null) ? vatAmount : BigDecimal.ZERO;
            this.grossAmount = netAmount.add(vat);
        }
    }
}
