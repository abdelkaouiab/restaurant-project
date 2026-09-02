package com.restaurant.management.entity;
import com.restaurant.management.enums.TypeMouvement; import jakarta.persistence.*; import java.math.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="mouvements_stock")
public class MouvementStock {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="ingredient_id") private Ingredient ingredient;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="utilisateur_id") private Utilisateur utilisateur;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="commande_id") private Commande commande;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="reception_achat_id") private ReceptionAchat receptionAchat;
 @Column(name="cle_idempotence",unique=true,updatable=false) private UUID cleIdempotence;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private TypeMouvement type;
 @Column(nullable=false,precision=14,scale=3) private BigDecimal quantite;
 @Column(nullable=false,precision=14,scale=3) private BigDecimal variation;
 @Column(name="quantite_apres",nullable=false,precision=14,scale=3) private BigDecimal quantiteApres;
 @Column(length=500) private String motif;
 @Column(name="cree_le",nullable=false,updatable=false) private Instant creeLe;
 protected MouvementStock(){}
 public MouvementStock(Ingredient i,Utilisateur u,Commande c,UUID key,TypeMouvement t,BigDecimal q,BigDecimal v,String motif){i.verifierQuantite(q);i.verifierQuantite(v);ingredient=i;utilisateur=u;commande=c;cleIdempotence=key;type=t;quantite=q.abs().setScale(3);variation=v.setScale(3);quantiteApres=i.getQuantiteDisponible();this.motif=motif;}
 public MouvementStock(Ingredient i,Utilisateur u,ReceptionAchat reception,BigDecimal q,String motif){i.verifierQuantite(q);ingredient=i;utilisateur=u;receptionAchat=reception;type=TypeMouvement.ENTREE;quantite=q.setScale(3);variation=quantite;quantiteApres=i.getQuantiteDisponible();this.motif=motif;}
 @PrePersist void creer(){creeLe=Instant.now();}
 public Long getId(){return id;} public Ingredient getIngredient(){return ingredient;} public Commande getCommande(){return commande;} public UUID getCleIdempotence(){return cleIdempotence;} public TypeMouvement getType(){return type;} public BigDecimal getQuantite(){return quantite;} public BigDecimal getVariation(){return variation;} public BigDecimal getQuantiteApres(){return quantiteApres;} public String getMotif(){return motif;} public Instant getCreeLe(){return creeLe;}
}
