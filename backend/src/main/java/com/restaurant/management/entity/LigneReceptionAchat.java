package com.restaurant.management.entity;
import jakarta.persistence.*; import java.math.*;
@Entity @Table(name="lignes_reception_achat")
public class LigneReceptionAchat {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="reception_id") private ReceptionAchat reception;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="ligne_achat_id") private LigneAchat ligneAchat;
 @Column(nullable=false,precision=14,scale=3) private BigDecimal quantite;
 protected LigneReceptionAchat(){} LigneReceptionAchat(ReceptionAchat r,LigneAchat l,BigDecimal q){reception=r;ligneAchat=l;l.getIngredient().verifierQuantite(q);quantite=q.setScale(3,RoundingMode.UNNECESSARY);}
 public Long getId(){return id;} public LigneAchat getLigneAchat(){return ligneAchat;} public BigDecimal getQuantite(){return quantite;}
}
