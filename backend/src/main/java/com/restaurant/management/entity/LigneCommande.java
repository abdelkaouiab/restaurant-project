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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "lignes_commande")
public class LigneCommande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produit_id", nullable = false)
    private Produit produit;

    @Column(nullable = false)
    private int quantite;

    @Column(name = "prix_unitaire", nullable = false, precision = 12, scale = 2)
    private BigDecimal prixUnitaire;

    @Column(name = "sous_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal sousTotal;

    @Column(length = 500)
    private String remarque;

    @Version
    @Column(nullable = false)
    private long version;

    protected LigneCommande() {
    }

    LigneCommande(Commande commande, Produit produit, int quantite, BigDecimal prixUnitaire, String remarque) {
        this.commande = commande;
        this.produit = produit;
        this.prixUnitaire = prixUnitaire.setScale(2, RoundingMode.HALF_UP);
        this.remarque = normaliserRemarque(remarque);
        modifierQuantite(quantite);
    }

    void modifierQuantite(int quantite) {
        this.quantite = quantite;
        this.sousTotal = prixUnitaire.multiply(BigDecimal.valueOf(quantite))
                .setScale(2, RoundingMode.HALF_UP);
    }

    void modifier(int quantite, String remarque) {
        modifierQuantite(quantite);
        this.remarque = normaliserRemarque(remarque);
    }

    private String normaliserRemarque(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    public Long getId() { return id; }
    public Commande getCommande() { return commande; }
    public Produit getProduit() { return produit; }
    public int getQuantite() { return quantite; }
    public BigDecimal getPrixUnitaire() { return prixUnitaire; }
    public BigDecimal getSousTotal() { return sousTotal; }
    public String getRemarque() { return remarque; }
    public long getVersion() { return version; }
}
