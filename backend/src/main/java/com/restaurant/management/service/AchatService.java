package com.restaurant.management.service;

import com.restaurant.management.dto.*;
import com.restaurant.management.entity.*;
import com.restaurant.management.enums.StatutAchat;
import com.restaurant.management.exception.*;
import com.restaurant.management.repository.*;
import com.restaurant.management.security.RestaurantUserPrincipal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AchatService {
 private static final Map<String,String> TRI=Map.of("numero","numero","statut","statut","montantTotal","montantTotal","creeLe","creeLe");
 private final FournisseurRepository fournisseurs; private final AchatRepository achats; private final IngredientRepository ingredients; private final ReceptionAchatRepository receptions; private final UtilisateurRepository utilisateurs; private final StockService stock; private final JournalModificationService journal;
 public AchatService(FournisseurRepository f,AchatRepository a,IngredientRepository i,ReceptionAchatRepository r,UtilisateurRepository u,StockService s,JournalModificationService j){fournisseurs=f;achats=a;ingredients=i;receptions=r;utilisateurs=u;stock=s;journal=j;}

 @Transactional @PreAuthorize("hasAuthority('ACHAT_GERER')")
 public FournisseurResponse creerFournisseur(FournisseurRequest r){if(fournisseurs.existsByNomIgnoreCase(r.nom().strip()))throw new BusinessConflictException("Ce fournisseur existe déjà.");return fournisseur(fournisseurs.saveAndFlush(new Fournisseur(r.nom(),r.email(),r.telephone(),r.adresse())));}
 @Transactional @PreAuthorize("hasAuthority('ACHAT_GERER')")
 public FournisseurResponse modifierFournisseur(Long id,FournisseurRequest r){Fournisseur f=fournisseurs.findById(id).orElseThrow(ResourceNotFoundException::new);f.modifier(r.nom(),r.email(),r.telephone(),r.adresse());fournisseurs.flush();return fournisseur(f);}
 @Transactional @PreAuthorize("hasAuthority('ACHAT_GERER')")
 public FournisseurResponse modifierStatutFournisseur(Long id,ModifierStatutFournisseurRequest r){
   Fournisseur f=fournisseurs.findById(id).orElseThrow(ResourceNotFoundException::new);
   if(f.getVersion()!=r.version())throw new BusinessConflictException("Le fournisseur a été modifié depuis son affichage.");
   if(f.isActif()==r.actif())return fournisseur(f);
   f.setActif(r.actif());fournisseurs.flush();
   journal.journaliser(r.actif()?"FOURNISSEUR_REACTIVATION":"FOURNISSEUR_DESACTIVATION","FOURNISSEUR",id);
   return fournisseur(f);
 }
 @Transactional(readOnly=true) @PreAuthorize("hasAuthority('ACHAT_CONSULTER')")
 public PageResponse<FournisseurResponse> fournisseurs(String recherche,int page,int taille,String tri,String direction){String terme=RechercheServiceSupport.terme(recherche);Specification<Fournisseur> spec=(root,q,cb)->terme==null?cb.conjunction():cb.like(cb.lower(root.get("nom")),"%"+terme+"%");return PageResponse.depuis(fournisseurs.findAll(spec,RechercheServiceSupport.pageable(page,taille,tri,direction,Map.of("nom","nom","creeLe","creeLe"))).map(this::fournisseur));}

 @Transactional @PreAuthorize("hasAuthority('ACHAT_GERER')")
 public AchatResponse creer(CreerAchatRequest r){Fournisseur f=fournisseurs.findByIdAndActifTrue(r.fournisseurId()).orElseThrow(ResourceNotFoundException::new);Utilisateur u=utilisateur();Achat a=achats.saveAndFlush(new Achat(numero(),f,u));journal.journaliser("ACHAT_CREATION","ACHAT",a.getId());return achat(a);}
 @Transactional @PreAuthorize("hasAuthority('ACHAT_GERER')")
 public AchatResponse ajouterLigne(Long id,AjouterLigneAchatRequest r){Achat a=verrou(id);Ingredient i=ingredients.findById(r.ingredientId()).filter(Ingredient::isActif).orElseThrow(ResourceNotFoundException::new);transition(()->a.ajouterLigne(i,r.quantite(),r.prixUnitaire()));achats.flush();return achat(a);}
 @Transactional @PreAuthorize("hasAuthority('ACHAT_GERER')")
 public AchatResponse commander(Long id){Achat a=verrou(id);transition(a::commander);achats.flush();journal.journaliser("ACHAT_COMMANDE","ACHAT",id);return achat(a);}
 @Transactional @PreAuthorize("hasAuthority('ACHAT_GERER')")
 public AchatResponse annuler(Long id){Achat a=verrou(id);transition(a::annuler);achats.flush();journal.journaliser("ACHAT_ANNULATION","ACHAT",id);return achat(a);}

 @Transactional @PreAuthorize("hasAuthority('ACHAT_GERER')")
 public AchatResponse recevoir(Long id,UUID cle,ReceptionAchatRequest r){
   String signature=signature(r); ReceptionAchat existante=receptions.findByCleIdempotence(cle).orElse(null);
   if(existante!=null)return verifierReception(existante,id,signature);
   Achat a=verrou(id); existante=receptions.findByCleIdempotence(cle).orElse(null); if(existante!=null)return verifierReception(existante,id,signature);
   Set<Long> ids=new HashSet<>(); Map<LigneAchat,BigDecimal> quantites=new LinkedHashMap<>();
   for(LigneReceptionAchatRequest ligne:r.lignes()){
     if(!ids.add(ligne.ligneAchatId()))throw new BusinessConflictException("Une ligne ne peut apparaître qu'une fois par réception.");
     LigneAchat la; try{la=a.trouverLigne(ligne.ligneAchatId());}catch(IllegalArgumentException e){throw new ResourceNotFoundException();}
     quantites.put(la,ligne.quantite());
   }
   ReceptionAchat reception=new ReceptionAchat(a,cle,signature,utilisateur());
   for(var e:quantites.entrySet()){transition(()->a.enregistrerReception(e.getKey(),e.getValue()));reception.ajouter(e.getKey(),e.getValue());}
   receptions.saveAndFlush(reception);
   Map<Ingredient,BigDecimal> entrees=new HashMap<>();quantites.forEach((l,q)->entrees.merge(l.getIngredient(),q,BigDecimal::add));stock.enregistrerReception(reception,utilisateur(),entrees);
   achats.flush();journal.journaliser("ACHAT_RECEPTION","RECEPTION_ACHAT",reception.getId());return achat(a);
 }
 @Transactional(readOnly=true) @PreAuthorize("hasAuthority('ACHAT_CONSULTER')") public AchatResponse obtenir(Long id){return achat(achats.findById(id).orElseThrow(ResourceNotFoundException::new));}
 @Transactional(readOnly=true) @PreAuthorize("hasAuthority('ACHAT_CONSULTER')")
 public List<ReceptionAchatResponse> receptions(Long id){
   if(!achats.existsById(id))throw new ResourceNotFoundException();
   return receptions.findDistinctByAchatIdOrderByCreeLeDescIdDesc(id).stream().map(this::reception).toList();
 }
 @Transactional(readOnly=true) @PreAuthorize("hasAuthority('ACHAT_CONSULTER')")
 public PageResponse<AchatResponse> rechercher(String recherche,StatutAchat statut,int page,int taille,String tri,String direction){String terme=RechercheServiceSupport.terme(recherche);Specification<Achat> spec=(root,q,cb)->{List<Predicate> p=new ArrayList<>();if(statut!=null)p.add(cb.equal(root.get("statut"),statut));if(terme!=null)p.add(cb.like(cb.lower(root.get("numero")),"%"+terme+"%"));return cb.and(p.toArray(Predicate[]::new));};Page<AchatResponse> resultat=achats.findAll(spec,RechercheServiceSupport.pageable(page,taille,tri,direction,TRI)).map(this::achat);return PageResponse.depuis(resultat);}

 private Achat verrou(Long id){return achats.findByIdAvecVerrou(id).orElseThrow(ResourceNotFoundException::new);}
 private AchatResponse verifierReception(ReceptionAchat r,Long achatId,String signature){if(!r.getAchat().getId().equals(achatId)||!r.getSignatureRequete().equals(signature))throw new BusinessConflictException("La clé d'idempotence a déjà été utilisée.");return achat(r.getAchat());}
 private String signature(ReceptionAchatRequest r){return r.lignes().stream().sorted(Comparator.comparing(LigneReceptionAchatRequest::ligneAchatId)).map(l->l.ligneAchatId()+":"+l.quantite().stripTrailingZeros().toPlainString()).reduce((a,b)->a+","+b).orElseThrow();}
 private void transition(Runnable action){try{action.run();}catch(IllegalStateException|IllegalArgumentException|ArithmeticException e){throw new BusinessConflictException(e.getMessage());}}
 private Utilisateur utilisateur(){return utilisateurs.findById(principal().getUtilisateurId()).orElseThrow(ResourceNotFoundException::new);}
 private RestaurantUserPrincipal principal(){Authentication a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!(a.getPrincipal() instanceof RestaurantUserPrincipal p))throw new AccessDeniedException("Authentification requise.");return p;}
 private String numero(){return "ACH-"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);}
 private FournisseurResponse fournisseur(Fournisseur f){return new FournisseurResponse(f.getId(),f.getNom(),f.getEmail(),f.getTelephone(),f.getAdresse(),f.isActif(),f.getVersion());}
 private AchatResponse achat(Achat a){List<LigneAchatResponse> lignes=a.getLignes().stream().map(l->new LigneAchatResponse(l.getId(),l.getIngredient().getId(),l.getIngredient().getNom(),l.getIngredient().getUnite(),l.getQuantiteCommandee(),l.getQuantiteRecue(),l.getQuantiteRestante(),l.getPrixUnitaire(),l.getSousTotal(),l.getVersion())).toList();return new AchatResponse(a.getId(),a.getNumero(),fournisseur(a.getFournisseur()),a.getStatut(),a.getMontantTotal(),lignes,a.getCommandeLe(),a.getRecueLe(),a.getAnnuleLe(),a.getCreeLe(),a.getModifieLe(),a.getVersion());}
 private ReceptionAchatResponse reception(ReceptionAchat r){List<LigneReceptionAchatResponse> lignes=r.getLignes().stream().map(l->{LigneAchat achat=l.getLigneAchat();Ingredient ingredient=achat.getIngredient();return new LigneReceptionAchatResponse(l.getId(),achat.getId(),ingredient.getId(),ingredient.getNom(),ingredient.getUnite(),l.getQuantite());}).toList();return new ReceptionAchatResponse(r.getId(),r.getCreeLe(),lignes);}
}
