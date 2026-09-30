package pl.ngo.budget.entity.coverage;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "planned_events")
@Getter
@Setter
public class PlannedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private LocalDate eventDate;

    @Column(precision = 12, scale = 2)
    private BigDecimal plannedCost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grant_id")
    private Grant grant;

    @Column(nullable = false)
    private boolean active = true;
}
