package com.restaurant.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "produits")
public class Produit extends AbstractActiveEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nom;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal prix;

    @Column(nullable = false)
    private boolean disponible = true;

    @Column(name = "image_nom_fichier", length = 80)
    private String imageNomFichier;

    @Column(name = "image_type_mime", length = 20)
    private String imageTypeMime;

    @Column(name = "image_taille")
    private Long imageTaille;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categorie_id", nullable = false)
    private Categorie categorie;

    protected Produit() {
    }

    public Produit(String nom, String description, BigDecimal prix, Categorie categorie) {
        modifier(nom, description, prix, categorie, true);
    }

    public void modifier(String nom, String description, BigDecimal prix, Categorie categorie) {
        modifier(nom, description, prix, categorie, disponible);
    }

    public void modifier(String nom, String description, BigDecimal prix, Categorie categorie, boolean disponible) {
        this.nom = nom.strip();
        this.description = description == null || description.isBlank() ? null : description.strip();
        this.prix = prix;
        this.categorie = categorie;
        this.disponible = disponible;
    }

    public void definirImage(String nomFichier, String typeMime, long taille) {
        imageNomFichier = nomFichier;
        imageTypeMime = typeMime;
        imageTaille = taille;
    }

    public void retirerImage() {
        imageNomFichier = null;
        imageTypeMime = null;
        imageTaille = null;
    }

    public Long getId() { return id; }
    public String getNom() { return nom; }
    public String getDescription() { return description; }
    public BigDecimal getPrix() { return prix; }
    public Categorie getCategorie() { return categorie; }
    public boolean isDisponible() { return disponible; }
    public boolean possedeImage() { return imageNomFichier != null; }
    public String getImageNomFichier() { return imageNomFichier; }
    public String getImageTypeMime() { return imageTypeMime; }
    public Long getImageTaille() { return imageTaille; }
}
