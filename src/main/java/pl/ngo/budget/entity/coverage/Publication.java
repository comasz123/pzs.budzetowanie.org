package pl.ngo.budget.entity.coverage;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "publications")
@Getter
@Setter
public class Publication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(precision = 12, scale = 2)
    private BigDecimal plannedCost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grant_id")
    private Grant grant;

    @Column(nullable = false)
    private boolean active = true;
}
