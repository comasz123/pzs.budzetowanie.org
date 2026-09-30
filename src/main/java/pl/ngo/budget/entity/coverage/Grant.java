package pl.ngo.budget.entity.coverage;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pl.ngo.budget.entity.cost.Employee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "grants")
@Getter
@Setter
public class Grant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sponsor_id", nullable = false)
    private Sponsor sponsor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    private LocalDate startDate;
    private LocalDate endDate;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalAmount;

    private String currency;

    @OneToMany(mappedBy = "grant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GrantBudgetItem> budgetItems = new ArrayList<>();

    @OneToMany(mappedBy = "grant", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("trancheNumber ASC")
    private List<GrantTranche> tranches = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_sponsor_contact_id")
    private SponsorContact primarySponsorContact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "financial_sponsor_contact_id")
    private SponsorContact financialSponsorContact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grant_coordinator_id")
    private Employee grantCoordinator;

    @Column(nullable = false)
    private boolean active = true;
}
