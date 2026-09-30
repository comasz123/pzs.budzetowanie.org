package pl.ngo.budget.entity.coverage;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pl.ngo.budget.entity.cost.Employee;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "projects")
@Getter
@Setter
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDate startDate;
    private LocalDate endDate;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalBudget;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grant_coordinator_id")
    private Employee grantCoordinator;

    @Column(nullable = false)
    private boolean active = true;
}
