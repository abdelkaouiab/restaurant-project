package com.restaurant.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "clients")
public class Client extends AbstractActiveEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, length = 100)
    private String prenom;

    @Column(length = 254)
    private String email;

    @Column(length = 30)
    private String telephone;

    protected Client() {
    }

    public Client(String nom, String prenom, String email, String telephone) {
        modifier(nom, prenom, email, telephone);
    }

    public void modifier(String nom, String prenom, String email, String telephone) {
        this.nom = nom.strip();
        this.prenom = prenom.strip();
        this.email = normaliserNullable(email);
        this.telephone = normaliserNullable(telephone);
    }

    private String normaliserNullable(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.strip();
    }

    public Long getId() { return id; }
    public String getNom() { return nom; }
    public String getPrenom() { return prenom; }
    public String getEmail() { return email; }
    public String getTelephone() { return telephone; }
}
