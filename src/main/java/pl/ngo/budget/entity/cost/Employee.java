package pl.ngo.budget.entity.cost;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "employees")
@Getter
@Setter
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String email;

    private String phone;

    private String position;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ContractType contractType;

    @Column(precision = 12, scale = 2)
    private BigDecimal plannedCost;

    /** Początek pracy w budżecie (rozpis pensji na miesiące); null = od początku roku. */
    private java.time.LocalDate budgetFrom;

    /** Koniec pracy w budżecie; null = do końca roku. */
    private java.time.LocalDate budgetTo;

    /** Miesiące roku {@code year} (1–12), w których pracownik pracuje według okresu od–do. */
    public java.util.List<Integer> budgetMonths(int year) {
        java.time.LocalDate from = budgetFrom != null ? budgetFrom : java.time.LocalDate.of(year, 1, 1);
        java.time.LocalDate to = budgetTo != null ? budgetTo : java.time.LocalDate.of(year, 12, 31);
        java.util.List<Integer> months = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            java.time.LocalDate first = java.time.LocalDate.of(year, month, 1);
            java.time.LocalDate last = first.withDayOfMonth(first.lengthOfMonth());
            if (!last.isBefore(from) && !first.isAfter(to)) {
                months.add(month);
            }
        }
        return months;
    }

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CostAllocation> costAllocations = new ArrayList<>();
}
