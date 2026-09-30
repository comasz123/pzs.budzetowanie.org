package pl.ngo.budget.entity.coverage;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sponsors")
@Getter
@Setter
public class Sponsor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String type;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private String street;
    private String postalCode;
    private String city;
    private String country;
    private String email;
    private String phone;
}
