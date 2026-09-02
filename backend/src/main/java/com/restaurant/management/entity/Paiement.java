package com.restaurant.management.entity;

import com.restaurant.management.enums.ModePaiement;
import com.restaurant.management.enums.StatutPaiement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "paiements")
public class Paiement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encaisse_par_id", nullable = false)
    private Utilisateur encaissePar;

    @Column(name = "cle_idempotence", nullable = false, unique = true, updatable = false)
    private UUID cleIdempotence;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private ModePaiement mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private StatutPaiement statut = StatutPaiement.VALIDE;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe;

    @Version
    @Column(nullable = false)
    private long version;

    protected Paiement() {
    }

    public Paiement(
            Commande commande,
            Utilisateur encaissePar,
            UUID cleIdempotence,
            BigDecimal montant,
            ModePaiement mode
    ) {
        this.commande = commande;
        this.encaissePar = encaissePar;
        this.cleIdempotence = cleIdempotence;
        this.montant = montant.setScale(2, RoundingMode.UNNECESSARY);
        this.mode = mode;
    }

    @PrePersist
    void initialiserDate() {
        creeLe = Instant.now();
    }

    public Long getId() { return id; }
    public Commande getCommande() { return commande; }
    public Utilisateur getEncaissePar() { return encaissePar; }
    public UUID getCleIdempotence() { return cleIdempotence; }
    public BigDecimal getMontant() { return montant; }
    public ModePaiement getMode() { return mode; }
    public StatutPaiement getStatut() { return statut; }
    public Instant getCreeLe() { return creeLe; }
    public long getVersion() { return version; }
}
