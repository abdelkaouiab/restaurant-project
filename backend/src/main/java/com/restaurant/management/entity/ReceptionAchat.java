package com.restaurant.management.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity @Table(name="receptions_achat")
public class ReceptionAchat {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="achat_id") private Achat achat;
 @Column(name="cle_idempotence",nullable=false,unique=true,updatable=false) private UUID cleIdempotence;
 @Column(name="signature_requete",nullable=false,length=1000,updatable=false) private String signatureRequete;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="recue_par_id") private Utilisateur recuePar;
 @OneToMany(mappedBy="reception",cascade=CascadeType.ALL) private List<LigneReceptionAchat> lignes=new ArrayList<>();
 @Column(name="cree_le",nullable=false,updatable=false) private Instant creeLe;
 protected ReceptionAchat(){}
 public ReceptionAchat(Achat a,UUID cle,String signature,Utilisateur utilisateur){achat=a;cleIdempotence=cle;signatureRequete=signature;recuePar=utilisateur;}
 public void ajouter(LigneAchat ligne,java.math.BigDecimal quantite){lignes.add(new LigneReceptionAchat(this,ligne,quantite));}
 @PrePersist void creer(){creeLe=Instant.now();}
 public Long getId(){return id;} public Achat getAchat(){return achat;} public UUID getCleIdempotence(){return cleIdempotence;} public String getSignatureRequete(){return signatureRequete;} public List<LigneReceptionAchat> getLignes(){return Collections.unmodifiableList(lignes);} public Instant getCreeLe(){return creeLe;}
}
