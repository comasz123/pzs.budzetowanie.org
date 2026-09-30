package pl.ngo.budget.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "organizations")
@Getter
@Setter
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String legalName;

    private String nip;
    private String regon;

    private String street;
    private String postalCode;
    private String city;

    private String email;
    private String phone;
    private String website;

    /** Domena tenantu, np. ngo.budzetowanie.org */
    @Column(nullable = false, unique = true)
    private String host;

    @Column(nullable = false)
    private boolean demo = false;

    @Column(nullable = false)
    private boolean active = true;
}
