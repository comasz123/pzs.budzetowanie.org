package pl.ngo.budget.entity.coverage;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "grant_budget_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GrantBudgetItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grant_id", nullable = false)
    private Grant grant;

    @Column(nullable = false)
    private String name;

    private String code;

    private String accountingCode;

    @Column(precision = 12, scale = 2)
    private BigDecimal plannedAmount;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "grantBudgetItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GrantBudgetItemCoverage> coverages = new ArrayList<>();
}
