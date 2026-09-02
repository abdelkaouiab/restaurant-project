package com.restaurant.management.entity;

import com.restaurant.management.enums.UniteMesure;
import com.restaurant.management.validation.QuantiteIngredient;
import jakarta.persistence.*;
import java.math.*;
import java.time.Instant;

@Entity @Table(name = "ingredients")
public class Ingredient {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=150,unique=true) private String nom;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private UniteMesure unite;
    @Column(name="quantite_disponible",nullable=false,precision=14,scale=3)
    private BigDecimal quantiteDisponible = BigDecimal.ZERO.setScale(3);
    @Column(name="seuil_alerte",nullable=false,precision=14,scale=3) private BigDecimal seuilAlerte;
    @Column(nullable=false) private boolean actif = true;
    @Column(name="cree_le",nullable=false,updatable=false) private Instant creeLe;
    @Column(name="modifie_le",nullable=false) private Instant modifieLe;
    @Version @Column(nullable=false) private long version;
    protected Ingredient() {}
    public Ingredient(String nom, UniteMesure unite, BigDecimal seuilAlerte) { modifier(nom, unite, seuilAlerte); }
    public void modifier(String nom, UniteMesure unite, BigDecimal seuilAlerte) {
        BigDecimal seuilNormalise=echelle(seuilAlerte);
        QuantiteIngredient.verifier(unite,seuilNormalise);
        QuantiteIngredient.verifier(unite,quantiteDisponible);
        this.nom=nom.strip(); this.unite=unite; this.seuilAlerte=seuilNormalise;
    }
    public void appliquer(BigDecimal variation, boolean negatifAutorise) {
        BigDecimal variationNormalisee=echelle(variation);
        QuantiteIngredient.verifier(unite,variationNormalisee);
        BigDecimal apres=quantiteDisponible.add(variationNormalisee);
        QuantiteIngredient.verifier(unite,apres);
        if (apres.signum()<0 && !negatifAutorise) throw new IllegalStateException("Stock insuffisant.");
        quantiteDisponible=apres;
    }
    public void verifierQuantite(BigDecimal quantite) { QuantiteIngredient.verifier(unite,echelle(quantite)); }
    private BigDecimal echelle(BigDecimal v) { return v.setScale(3, RoundingMode.UNNECESSARY); }
    @PrePersist void creer(){creeLe=Instant.now();modifieLe=creeLe;}
    @PreUpdate void modifierDate(){modifieLe=Instant.now();}
    public Long getId(){return id;} public String getNom(){return nom;} public UniteMesure getUnite(){return unite;}
    public BigDecimal getQuantiteDisponible(){return quantiteDisponible;} public BigDecimal getSeuilAlerte(){return seuilAlerte;}
    public boolean isActif(){return actif;} public void setActif(boolean actif){this.actif=actif;}
    public boolean isStockFaible(){return quantiteDisponible.compareTo(seuilAlerte)<=0;}
    public long getVersion(){return version;}
}
