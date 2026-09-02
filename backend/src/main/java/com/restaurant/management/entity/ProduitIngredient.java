package com.restaurant.management.entity;
import jakarta.persistence.*; import java.math.*;
@Entity @Table(name="produit_ingredients")
public class ProduitIngredient {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="produit_id") private Produit produit;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="ingredient_id") private Ingredient ingredient;
 @Column(nullable=false,precision=14,scale=3) private BigDecimal quantite;
 @Version @Column(nullable=false) private long version;
 protected ProduitIngredient(){} public ProduitIngredient(Produit p,Ingredient i,BigDecimal q){produit=p;ingredient=i;modifierQuantite(q);}
 public void modifierQuantite(BigDecimal q){ingredient.verifierQuantite(q);quantite=q.setScale(3,RoundingMode.UNNECESSARY);}
 public Long getId(){return id;} public Produit getProduit(){return produit;} public Ingredient getIngredient(){return ingredient;} public BigDecimal getQuantite(){return quantite;} public long getVersion(){return version;}
}
