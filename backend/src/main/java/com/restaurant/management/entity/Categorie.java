package com.restaurant.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Categorie extends AbstractActiveEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(length = 500)
    private String description;

    protected Categorie() {
    }

    public Categorie(String nom, String description) {
        modifier(nom, description);
    }

    public void modifier(String nom, String description) {
        this.nom = nom.strip();
        this.description = description == null || description.isBlank() ? null : description.strip();
    }

    public Long getId() { return id; }
    public String getNom() { return nom; }
    public String getDescription() { return description; }
}
