package com.restaurant.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "fournisseurs")
public class Fournisseur extends AbstractActiveEntity {
    @jakarta.persistence.Id @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 150, unique = true) private String nom;
    @Column(length = 254, unique = true) private String email;
    @Column(length = 30) private String telephone;
    @Column(length = 500) private String adresse;

    protected Fournisseur() {}

    public Fournisseur(String nom, String email, String telephone, String adresse) {
        modifier(nom, email, telephone, adresse);
    }

    public void modifier(String nom, String email, String telephone, String adresse) {
        this.nom = nom.strip();
        this.email = normaliser(email, true);
        this.telephone = normaliser(telephone, false);
        this.adresse = normaliser(adresse, false);
    }

    private String normaliser(String valeur, boolean minuscule) {
        if (valeur == null || valeur.isBlank()) return null;
        String resultat = valeur.strip();
        return minuscule ? resultat.toLowerCase(java.util.Locale.ROOT) : resultat;
    }

    public Long getId() { return id; }
    public String getNom() { return nom; }
    public String getEmail() { return email; }
    public String getTelephone() { return telephone; }
    public String getAdresse() { return adresse; }
}
