package com.restaurant.management.service;

import com.restaurant.management.dto.*; import com.restaurant.management.entity.*; import com.restaurant.management.enums.*;
import com.restaurant.management.exception.*; import com.restaurant.management.repository.*; import com.restaurant.management.security.RestaurantUserPrincipal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest; import org.springframework.data.domain.Sort; import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.security.core.*; import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.math.*; import java.util.*; import java.util.stream.Collectors;

@Service
public class StockService {
 private final IngredientRepository ingredients; private final ProduitIngredientRepository recettes; private final MouvementStockRepository mouvements; private final ProduitRepository produits; private final UtilisateurRepository utilisateurs; private final JournalModificationService journal;
 public StockService(IngredientRepository i,ProduitIngredientRepository r,MouvementStockRepository m,ProduitRepository p,UtilisateurRepository u,JournalModificationService j){ingredients=i;recettes=r;mouvements=m;produits=p;utilisateurs=u;journal=j;}

 @Transactional @PreAuthorize("hasAuthority('STOCK_GERER')")
 public IngredientResponse creerIngredient(IngredientRequest r){if(ingredients.existsByNomIgnoreCase(r.nom().strip()))throw new BusinessConflictException("Cet ingrédient existe déjà.");try{return ingredient(ingredients.saveAndFlush(new Ingredient(r.nom(),r.unite(),r.seuilAlerte())));}catch(IllegalArgumentException e){throw new BusinessConflictException(e.getMessage());}}
 @Transactional(readOnly=true) @PreAuthorize("hasAuthority('STOCK_CONSULTER')")
 public List<IngredientResponse> listerIngredients(){return ingredients.findAllByOrderByNomAsc().stream().map(this::ingredient).toList();}
 @Transactional(readOnly=true) @PreAuthorize("hasAuthority('STOCK_CONSULTER')")
 public PageResponse<MouvementStockHistoriqueResponse> listerMouvements(Long ingredientId,TypeMouvement type,int page,int taille){
   Specification<MouvementStock> filtres=(root,query,cb)->{
     List<Predicate> predicates=new ArrayList<>();
     if(ingredientId!=null)predicates.add(cb.equal(root.get("ingredient").get("id"),ingredientId));
     if(type!=null)predicates.add(cb.equal(root.get("type"),type));
     return cb.and(predicates.toArray(Predicate[]::new));
   };
   return PageResponse.depuis(mouvements.findAll(filtres,PageRequest.of(page,taille,Sort.by(Sort.Order.desc("creeLe"),Sort.Order.desc("id")))).map(this::historique));
 }
 @Transactional @PreAuthorize("hasAuthority('STOCK_GERER')")
 public IngredientResponse modifierIngredient(Long id,IngredientRequest r){Ingredient i=ingredients.findById(id).orElseThrow(ResourceNotFoundException::new);try{i.modifier(r.nom(),r.unite(),r.seuilAlerte());}catch(IllegalArgumentException e){throw new BusinessConflictException(e.getMessage());}ingredients.flush();return ingredient(i);}

 @Transactional @PreAuthorize("hasAuthority('STOCK_GERER')")
 public RecetteIngredientResponse definirRecette(Long produitId,RecetteIngredientRequest r){Produit p=produits.findById(produitId).orElseThrow(ResourceNotFoundException::new);Ingredient i=ingredients.findById(r.ingredientId()).orElseThrow(ResourceNotFoundException::new);if(!i.isActif())throw new BusinessConflictException("L'ingrédient est inactif.");try{ProduitIngredient pi=recettes.findByProduitIdAndIngredientId(produitId,i.getId()).orElseGet(()->new ProduitIngredient(p,i,r.quantite()));pi.modifierQuantite(r.quantite());return recette(recettes.saveAndFlush(pi));}catch(IllegalArgumentException e){throw new BusinessConflictException(e.getMessage());}}
 @Transactional(readOnly=true) @PreAuthorize("hasAuthority('STOCK_CONSULTER')")
 public List<RecetteIngredientResponse> recette(Long produitId){if(!produits.existsById(produitId))throw new ResourceNotFoundException();return recettes.findByProduitId(produitId).stream().map(this::recette).toList();}
 @Transactional @PreAuthorize("hasAuthority('STOCK_GERER')")
 public void supprimerIngredientRecette(Long produitId,Long ingredientId,long version){
   ProduitIngredient association=recettes.findByProduitIdAndIngredientId(produitId,ingredientId).orElseThrow(ResourceNotFoundException::new);
   if(association.getVersion()!=version)throw new BusinessConflictException("La recette a été modifiée depuis son affichage.");
   Long associationId=association.getId();
   recettes.delete(association);recettes.flush();
   journal.journaliser("STOCK_RECETTE_INGREDIENT_SUPPRESSION","PRODUIT_INGREDIENT",associationId);
 }

 @Transactional @PreAuthorize("hasAuthority('STOCK_GERER')")
 public MouvementStockResponse mouvement(UUID cle,MouvementStockRequest r){
   MouvementStock ancien=mouvements.findByCleIdempotence(cle).orElse(null); if(ancien!=null)return verifierIdempotence(ancien,r);
   Ingredient i=ingredients.findByIdAvecVerrou(r.ingredientId()).orElseThrow(ResourceNotFoundException::new);
   ancien=mouvements.findByCleIdempotence(cle).orElse(null); if(ancien!=null)return verifierIdempotence(ancien,r);
   if(r.quantite().signum()==0)throw new BusinessConflictException("La quantité ne peut pas être nulle.");
   if((r.type()==TypeMouvement.AJUSTEMENT||r.type()==TypeMouvement.PERTE)&&(r.motif()==null||r.motif().isBlank()))throw new BusinessConflictException("Un motif est obligatoire.");
   BigDecimal variation=variation(r.type(),r.quantite()); boolean negatif=autorite("STOCK_NEGATIF_AUTORISER");
   try{i.appliquer(variation,negatif);}catch(IllegalStateException|IllegalArgumentException e){throw new BusinessConflictException(e.getMessage());}
   Utilisateur u=utilisateurs.findById(principal().getUtilisateurId()).orElseThrow(ResourceNotFoundException::new);
   MouvementStock m=mouvements.saveAndFlush(new MouvementStock(i,u,null,cle,r.type(),r.quantite(),variation,normaliser(r.motif())));
   journal.journaliser("STOCK_"+r.type(),"MOUVEMENT_STOCK",m.getId()); return mouvement(m);
 }

 public void deduireConfirmation(Commande commande){
   Map<Long,BigDecimal> besoins=new HashMap<>();
   Set<Long> produitIds=commande.getLignes().stream().map(l->l.getProduit().getId()).collect(Collectors.toSet());
   for(ProduitIngredient pi:recettes.findRecettes(produitIds)){
     int q=commande.getLignes().stream().filter(l->l.getProduit().getId().equals(pi.getProduit().getId())).findFirst().orElseThrow().getQuantite();
     besoins.merge(pi.getIngredient().getId(),pi.getQuantite().multiply(BigDecimal.valueOf(q)),BigDecimal::add);
   }
   for(Long id:besoins.keySet().stream().sorted().toList()){
     Ingredient i=ingredients.findByIdAvecVerrou(id).orElseThrow(ResourceNotFoundException::new);
     if(mouvements.existsByCommandeIdAndIngredientId(commande.getId(),id))continue;
     BigDecimal q=besoins.get(id).setScale(3); BigDecimal v=q.negate();
     try{i.appliquer(v,autorite("STOCK_NEGATIF_AUTORISER"));}catch(IllegalArgumentException e){throw new BusinessConflictException(e.getMessage());}catch(IllegalStateException e){throw new BusinessConflictException("Stock insuffisant pour "+i.getNom()+".");}
     MouvementStock m=mouvements.save(new MouvementStock(i,null,commande,null,TypeMouvement.SORTIE,q,v,"Consommation automatique à la confirmation"));
     journal.journaliser("STOCK_DEDUCTION_COMMANDE","MOUVEMENT_STOCK",mouvements.saveAndFlush(m).getId());
   }
 }
 public void enregistrerReception(ReceptionAchat reception,Utilisateur utilisateur,Map<Ingredient,BigDecimal> quantites){
   for(var entree:quantites.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(Ingredient::getId))).toList()){
     Ingredient i=ingredients.findByIdAvecVerrou(entree.getKey().getId()).orElseThrow(ResourceNotFoundException::new);
     BigDecimal q=entree.getValue().setScale(3,RoundingMode.UNNECESSARY); i.appliquer(q,false);
     mouvements.saveAndFlush(new MouvementStock(i,utilisateur,reception,q,"Réception de l'achat "+reception.getAchat().getNumero()));
   }
 }
 private BigDecimal variation(TypeMouvement t,BigDecimal q){if(t!=TypeMouvement.AJUSTEMENT&&q.signum()<0)throw new BusinessConflictException("La quantité doit être positive.");return switch(t){case ENTREE,RETOUR->q.abs();case SORTIE,PERTE->q.abs().negate();case AJUSTEMENT->q;};}
 private MouvementStockResponse verifierIdempotence(MouvementStock m,MouvementStockRequest r){if(!m.getIngredient().getId().equals(r.ingredientId())||m.getType()!=r.type()||m.getQuantite().compareTo(r.quantite().abs())!=0)throw new BusinessConflictException("La clé d'idempotence a déjà été utilisée.");return mouvement(m);}
 private IngredientResponse ingredient(Ingredient i){return new IngredientResponse(i.getId(),i.getNom(),i.getUnite(),i.getQuantiteDisponible(),i.getSeuilAlerte(),i.isStockFaible(),i.isActif(),i.getVersion());}
 private RecetteIngredientResponse recette(ProduitIngredient p){return new RecetteIngredientResponse(p.getIngredient().getId(),p.getIngredient().getNom(),p.getIngredient().getUnite(),p.getQuantite(),p.getVersion());}
 private MouvementStockResponse mouvement(MouvementStock m){return new MouvementStockResponse(m.getId(),m.getIngredient().getId(),m.getType(),m.getQuantite(),m.getVariation(),m.getQuantiteApres(),m.getMotif(),m.getCleIdempotence(),m.getCreeLe());}
 private MouvementStockHistoriqueResponse historique(MouvementStock m){return new MouvementStockHistoriqueResponse(m.getId(),m.getIngredient().getId(),m.getIngredient().getNom(),m.getType(),m.getQuantite(),m.getVariation(),m.getQuantiteApres(),m.getMotif(),m.getCreeLe());}
 private String normaliser(String s){return s==null||s.isBlank()?null:s.strip();}
 private boolean autorite(String a){Authentication x=SecurityContextHolder.getContext().getAuthentication();return x!=null&&x.getAuthorities().stream().anyMatch(y->y.getAuthority().equals(a));}
 private RestaurantUserPrincipal principal(){Authentication a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!(a.getPrincipal() instanceof RestaurantUserPrincipal p))throw new AccessDeniedException("Authentification requise.");return p;}
}
