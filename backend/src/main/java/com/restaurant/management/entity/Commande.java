package com.restaurant.management.entity;

import com.restaurant.management.enums.StatutCommande;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "commandes")
public class Commande {

    private static final int ECHELLE_MONTANT = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private TableRestaurant table;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cree_par_id", nullable = false)
    private Utilisateur creePar;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", unique = true)
    private Reservation reservation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutCommande statut = StatutCommande.BROUILLON;

    @Column(name = "montant_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal montantTotal = BigDecimal.ZERO.setScale(ECHELLE_MONTANT);

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneCommande> lignes = new ArrayList<>();

    @Column(name = "confirmee_le")
    private Instant confirmeeLe;

    @Column(name = "annulee_le")
    private Instant annuleeLe;

    @Column(name = "preparation_commencee_le")
    private Instant preparationCommenceeLe;

    @Column(name = "prete_le")
    private Instant preteLe;

    @Column(name = "payee_le")
    private Instant payeeLe;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe;

    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    @Version
    @Column(nullable = false)
    private long version;

    protected Commande() {
    }

    public Commande(String numero, TableRestaurant table, Client client, Utilisateur creePar) {
        this.numero = numero;
        this.table = table;
        this.client = client;
        this.creePar = creePar;
    }

    public Commande(String numero, Reservation reservation, Utilisateur creePar) {
        this(numero, reservation.getTable(), reservation.getClient(), creePar);
        this.reservation = reservation;
    }

    public void modifierAssociation(TableRestaurant table, Client client) {
        verifierBrouillon();
        this.table = table;
        this.client = client;
    }

    public void ajouterLigne(Produit produit, int quantite, BigDecimal prixUnitaire, String remarque) {
        verifierBrouillon();
        lignes.add(new LigneCommande(this, produit, quantite, prixUnitaire, remarque));
        recalculerTotal();
    }

    public void modifierLigne(Long ligneId, int quantite, String remarque) {
        verifierBrouillon();
        trouverLigne(ligneId).modifier(quantite, remarque);
        recalculerTotal();
    }

    public void modifierQuantite(Long ligneId, int quantite) {
        modifierLigne(ligneId, quantite, trouverLigne(ligneId).getRemarque());
    }

    public void supprimerLigne(Long ligneId) {
        verifierBrouillon();
        if (!lignes.removeIf(ligne -> ligne.getId().equals(ligneId))) {
            throw new IllegalArgumentException("Ligne absente de la commande.");
        }
        recalculerTotal();
    }

    public void confirmer() {
        verifierBrouillon();
        if (lignes.isEmpty()) {
            throw new IllegalStateException("Une commande doit contenir au moins une ligne.");
        }
        recalculerTotal();
        statut = StatutCommande.CONFIRMEE;
        confirmeeLe = Instant.now();
    }

    public void annuler() {
        if (statut != StatutCommande.BROUILLON && statut != StatutCommande.CONFIRMEE) {
            throw new IllegalStateException("Cette commande ne peut plus être annulée.");
        }
        statut = StatutCommande.ANNULEE;
        annuleeLe = Instant.now();
    }

    public void demarrerPreparation() {
        if (statut != StatutCommande.CONFIRMEE) {
            throw new IllegalStateException("Seule une commande confirmée peut passer en préparation.");
        }
        statut = StatutCommande.EN_PREPARATION;
        preparationCommenceeLe = Instant.now();
    }

    public void marquerPrete() {
        if (statut != StatutCommande.EN_PREPARATION) {
            throw new IllegalStateException("Seule une commande en préparation peut être marquée prête.");
        }
        statut = StatutCommande.PRETE;
        preteLe = Instant.now();
    }

    public void marquerPayee() {
        if (statut != StatutCommande.PRETE) {
            throw new IllegalStateException("Cette commande ne peut pas être marquée payée.");
        }
        statut = StatutCommande.PAYEE;
        payeeLe = Instant.now();
    }

    public void recalculerTotal() {
        montantTotal = lignes.stream()
                .map(LigneCommande::getSousTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ECHELLE_MONTANT, RoundingMode.HALF_UP);
    }

    private LigneCommande trouverLigne(Long ligneId) {
        return lignes.stream().filter(ligne -> ligne.getId().equals(ligneId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Ligne absente de la commande."));
    }

    private void verifierBrouillon() {
        if (statut != StatutCommande.BROUILLON) {
            throw new IllegalStateException("Seul un brouillon peut être modifié.");
        }
    }

    @PrePersist
    void initialiserDates() {
        Instant maintenant = Instant.now();
        creeLe = maintenant;
        modifieLe = maintenant;
    }

    @PreUpdate
    void modifierDate() {
        modifieLe = Instant.now();
    }

    public Long getId() { return id; }
    public String getNumero() { return numero; }
    public TableRestaurant getTable() { return table; }
    public Client getClient() { return client; }
    public Utilisateur getCreePar() { return creePar; }
    public Reservation getReservation() { return reservation; }
    public StatutCommande getStatut() { return statut; }
    public BigDecimal getMontantTotal() { return montantTotal; }
    public List<LigneCommande> getLignes() { return Collections.unmodifiableList(lignes); }
    public Instant getConfirmeeLe() { return confirmeeLe; }
    public Instant getAnnuleeLe() { return annuleeLe; }
    public Instant getPreparationCommenceeLe() { return preparationCommenceeLe; }
    public Instant getPreteLe() { return preteLe; }
    public Instant getPayeeLe() { return payeeLe; }
    public Instant getCreeLe() { return creeLe; }
    public Instant getModifieLe() { return modifieLe; }
    public long getVersion() { return version; }
}
