package pl.ngo.budget.entity.coverage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Przeniesienie linii wydatku (np. „Czynsz”) do innej kategorii/podkategorii budżetu. Linia jest
 * identyfikowana kluczem naturalnym (wiersz, pod którym powstaje, i nazwa), a nie bieżącym położeniem.
 */
@Entity
@Table(name = "budget_line_assignments")
@Getter
@Setter
public class BudgetLineAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_key", nullable = false, unique = true, length = 220)
    private String lineKey;

    @Column(name = "target_row_key", nullable = false, length = 120)
    private String targetRowKey;
}
