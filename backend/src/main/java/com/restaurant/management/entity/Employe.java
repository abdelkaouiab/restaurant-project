package com.restaurant.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "employes")
public class Employe extends AbstractActiveEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "matricule", nullable = false, length = 30)
    private String matricule;

    @Column(name = "nom", nullable = false, length = 100)
    private String nom;

    @Column(name = "prenom", nullable = false, length = 100)
    private String prenom;

    @Column(name = "telephone", length = 30)
    private String telephone;

    @Column(name = "poste", nullable = false, length = 100)
    private String poste;

    @Column(name = "date_embauche", nullable = false)
    private LocalDate dateEmbauche;

    protected Employe() {
    }

    public Employe(String matricule, String nom, String prenom, String poste, LocalDate dateEmbauche) {
        modifier(matricule, nom, prenom, null, poste, dateEmbauche);
    }

    public Long getId() {
        return id;
    }

    public String getMatricule() {
        return matricule;
    }

    public String getNom() {
        return nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public void modifier(
            String matricule,
            String nom,
            String prenom,
            String telephone,
            String poste,
            LocalDate dateEmbauche
    ) {
        this.matricule = matricule.strip();
        this.nom = nom.strip();
        this.prenom = prenom.strip();
        this.telephone = telephone == null ? null : telephone.strip();
        this.poste = poste.strip();
        this.dateEmbauche = dateEmbauche;
    }

    public String getPoste() {
        return poste;
    }

    public LocalDate getDateEmbauche() {
        return dateEmbauche;
    }
}
