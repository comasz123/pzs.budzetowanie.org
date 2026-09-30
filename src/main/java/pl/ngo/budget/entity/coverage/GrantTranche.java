package pl.ngo.budget.entity.coverage;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "grant_tranches")
@Getter
@Setter
public class GrantTranche {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grant_id", nullable = false)
    private Grant grant;

    @Column(nullable = false)
    private int trancheNumber;

    private LocalDate plannedDate;

    @Column(precision = 12, scale = 2)
    private BigDecimal plannedAmount;

    @Column(nullable = false)
    private boolean received = false;

    private LocalDate receivedDate;

    @Column(precision = 12, scale = 2)
    private BigDecimal receivedAmount;
}
