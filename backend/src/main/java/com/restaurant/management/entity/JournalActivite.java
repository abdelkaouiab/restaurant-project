package com.restaurant.management.entity;

import com.restaurant.management.enums.ResultatActivite;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "journaux_activite")
public class JournalActivite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "type_ressource", nullable = false, length = 100)
    private String typeRessource;

    @Column(name = "ressource_id", length = 100)
    private String ressourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultat", nullable = false, length = 20)
    private ResultatActivite resultat;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "adresse_ip", length = 45)
    private String adresseIp;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe;

    protected JournalActivite() {
    }

    public JournalActivite(
            Utilisateur utilisateur,
            String action,
            String typeRessource,
            String ressourceId,
            ResultatActivite resultat,
            UUID correlationId
    ) {
        this.utilisateur = utilisateur;
        this.action = action;
        this.typeRessource = typeRessource;
        this.ressourceId = ressourceId;
        this.resultat = resultat;
        this.correlationId = correlationId;
    }

    @PrePersist
    protected void initializeCreatedAt() {
        creeLe = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Utilisateur getUtilisateur() {
        return utilisateur;
    }

    public String getAction() {
        return action;
    }

    public String getTypeRessource() {
        return typeRessource;
    }

    public String getRessourceId() {
        return ressourceId;
    }

    public ResultatActivite getResultat() {
        return resultat;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getAdresseIp() {
        return adresseIp;
    }

    public void setAdresseIp(String adresseIp) {
        this.adresseIp = adresseIp;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public Instant getCreeLe() {
        return creeLe;
    }
}
