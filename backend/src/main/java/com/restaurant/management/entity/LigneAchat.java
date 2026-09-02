package com.restaurant.management.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "lignes_achat")
public class LigneAchat {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "achat_id") private Achat achat;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ingredient_id") private Ingredient ingredient;
    @Column(name="quantite_commandee",nullable=false,precision=14,scale=3) private BigDecimal quantiteCommandee;
    @Column(name="quantite_recue",nullable=false,precision=14,scale=3) private BigDecimal quantiteRecue=BigDecimal.ZERO.setScale(3);
    @Column(name="prix_unitaire",nullable=false,precision=14,scale=2) private BigDecimal prixUnitaire;
    @Column(name="sous_total",nullable=false,precision=14,scale=2) private BigDecimal sousTotal;
    @Version @Column(nullable=false) private long version;
    protected LigneAchat(){}
    LigneAchat(Achat achat,Ingredient ingredient,BigDecimal quantite,BigDecimal prix){this.achat=achat;this.ingredient=ingredient;ingredient.verifierQuantite(quantite);this.quantiteCommandee=quantite.setScale(3,RoundingMode.UNNECESSARY);this.prixUnitaire=prix.setScale(2,RoundingMode.HALF_UP);this.sousTotal=this.prixUnitaire.multiply(this.quantiteCommandee).setScale(2,RoundingMode.HALF_UP);}
    void recevoir(BigDecimal quantite){ingredient.verifierQuantite(quantite);BigDecimal q=quantite.setScale(3,RoundingMode.UNNECESSARY);if(q.signum()<=0||quantiteRecue.add(q).compareTo(quantiteCommandee)>0)throw new IllegalStateException("La quantité reçue dépasse la quantité restante.");quantiteRecue=quantiteRecue.add(q);}
    public boolean estCompletementRecue(){return quantiteRecue.compareTo(quantiteCommandee)==0;}
    public BigDecimal getQuantiteRestante(){return quantiteCommandee.subtract(quantiteRecue);}
    public Long getId(){return id;} public Achat getAchat(){return achat;} public Ingredient getIngredient(){return ingredient;} public BigDecimal getQuantiteCommandee(){return quantiteCommandee;} public BigDecimal getQuantiteRecue(){return quantiteRecue;} public BigDecimal getPrixUnitaire(){return prixUnitaire;} public BigDecimal getSousTotal(){return sousTotal;} public long getVersion(){return version;}
}
