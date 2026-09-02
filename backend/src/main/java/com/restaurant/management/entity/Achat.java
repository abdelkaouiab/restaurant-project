package com.restaurant.management.entity;

import com.restaurant.management.enums.StatutAchat;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "achats")
public class Achat {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 50, unique = true) private String numero;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "fournisseur_id") private Fournisseur fournisseur;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cree_par_id") private Utilisateur creePar;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 25) private StatutAchat statut = StatutAchat.BROUILLON;
    @Column(name = "montant_total", nullable = false, precision = 14, scale = 2) private BigDecimal montantTotal = BigDecimal.ZERO.setScale(2);
    @OneToMany(mappedBy = "achat", cascade = CascadeType.ALL, orphanRemoval = true) private List<LigneAchat> lignes = new ArrayList<>();
    @Column(name = "commande_le") private Instant commandeLe;
    @Column(name = "recue_le") private Instant recueLe;
    @Column(name = "annule_le") private Instant annuleLe;
    @Column(name = "cree_le", nullable = false, updatable = false) private Instant creeLe;
    @Column(name = "modifie_le", nullable = false) private Instant modifieLe;
    @Version @Column(nullable = false) private long version;

    protected Achat() {}
    public Achat(String numero, Fournisseur fournisseur, Utilisateur creePar) { this.numero=numero; this.fournisseur=fournisseur; this.creePar=creePar; }

    public void ajouterLigne(Ingredient ingredient, BigDecimal quantite, BigDecimal prixUnitaire) {
        verifierBrouillon();
        if (lignes.stream().anyMatch(l -> l.getIngredient().getId().equals(ingredient.getId()))) throw new IllegalStateException("Cet ingrédient est déjà présent.");
        lignes.add(new LigneAchat(this, ingredient, quantite, prixUnitaire));
        recalculerTotal();
    }
    public void commander() {
        verifierBrouillon();
        if (lignes.isEmpty()) throw new IllegalStateException("Un achat doit contenir au moins une ligne.");
        recalculerTotal(); statut=StatutAchat.COMMANDE; commandeLe=Instant.now();
    }
    public void enregistrerReception(LigneAchat ligne, BigDecimal quantite) {
        if (statut != StatutAchat.COMMANDE && statut != StatutAchat.PARTIELLEMENT_RECU) throw new IllegalStateException("Cet achat ne peut pas être réceptionné.");
        ligne.recevoir(quantite);
        boolean complet = lignes.stream().allMatch(LigneAchat::estCompletementRecue);
        statut = complet ? StatutAchat.RECU : StatutAchat.PARTIELLEMENT_RECU;
        if (complet) recueLe=Instant.now();
    }
    public void annuler() {
        if (statut != StatutAchat.BROUILLON && statut != StatutAchat.COMMANDE) throw new IllegalStateException("Cet achat ne peut pas être annulé.");
        statut=StatutAchat.ANNULE; annuleLe=Instant.now();
    }
    public void recalculerTotal() { montantTotal=lignes.stream().map(LigneAchat::getSousTotal).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2, RoundingMode.HALF_UP); }
    private void verifierBrouillon(){if(statut!=StatutAchat.BROUILLON)throw new IllegalStateException("Seul un achat brouillon peut être modifié.");}
    public LigneAchat trouverLigne(Long id){return lignes.stream().filter(l->l.getId().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Ligne absente de l'achat."));}
    @PrePersist void creer(){creeLe=Instant.now();modifieLe=creeLe;} @PreUpdate void modifier(){modifieLe=Instant.now();}
    public Long getId(){return id;} public String getNumero(){return numero;} public Fournisseur getFournisseur(){return fournisseur;} public Utilisateur getCreePar(){return creePar;} public StatutAchat getStatut(){return statut;} public BigDecimal getMontantTotal(){return montantTotal;} public List<LigneAchat> getLignes(){return Collections.unmodifiableList(lignes);} public Instant getCommandeLe(){return commandeLe;} public Instant getRecueLe(){return recueLe;} public Instant getAnnuleLe(){return annuleLe;} public Instant getCreeLe(){return creeLe;} public Instant getModifieLe(){return modifieLe;} public long getVersion(){return version;}
}
