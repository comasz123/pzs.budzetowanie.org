package pl.ngo.budget.entity.coverage;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "budget_item_templates")
@Getter
@Setter
public class BudgetItemTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String defaultCode;

    @Enumerated(EnumType.STRING)
    private CategoryType defaultCategory;

    @Column(nullable = false)
    private int displayOrder;

    public enum CategoryType {
        PERSONNEL, OFFICE, SERVICES, TRAVEL, EQUIPMENT, INDIRECT_COSTS, OTHER
    }
}
