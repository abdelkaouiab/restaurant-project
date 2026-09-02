package com.restaurant.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Immutable
@Table(name = "factures")
public class Facture {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commande_id", nullable = false, unique = true, updatable = false)
    private Commande commande;

    @Column(nullable = false, unique = true, length = 50, updatable = false)
    private String numero;

    @Column(name = "montant_total", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal montantTotal;

    @Column(name = "emise_le", nullable = false, updatable = false)
    private Instant emiseLe;

    @Version
    @Column(nullable = false)
    private long version;

    protected Facture() {
    }

    public Facture(Commande commande, String numero, BigDecimal montantTotal) {
        this.commande = commande;
        this.numero = numero;
        this.montantTotal = montantTotal.setScale(2, RoundingMode.UNNECESSARY);
    }

    @PrePersist
    void initialiserDate() {
        emiseLe = Instant.now();
    }

    public Long getId() { return id; }
    public Commande getCommande() { return commande; }
    public String getNumero() { return numero; }
    public BigDecimal getMontantTotal() { return montantTotal; }
    public Instant getEmiseLe() { return emiseLe; }
    public long getVersion() { return version; }
}
