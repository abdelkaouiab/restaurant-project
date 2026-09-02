package com.restaurant.management.controller;

import com.fasterxml.jackson.databind.*; import com.restaurant.management.entity.*; import com.restaurant.management.enums.*; import com.restaurant.management.repository.*; import com.restaurant.management.security.RestaurantUserPrincipal;
import org.junit.jupiter.api.Test; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.http.MediaType; import org.springframework.security.core.authority.SimpleGrantedAuthority; import org.springframework.test.annotation.DirtiesContext; import org.springframework.test.context.*; import org.springframework.test.web.servlet.*; import org.testcontainers.containers.PostgreSQLContainer; import org.testcontainers.junit.jupiter.*;
import java.math.*; import java.time.LocalDate; import java.util.*; import java.util.concurrent.*; import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat; import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*; import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*; import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers(disabledWithoutDocker=true) @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class StockIntegrationTest {
 static final AtomicInteger TABLES=new AtomicInteger(40000);
 @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("restaurant_stock_integration").withUsername("test").withPassword(UUID.randomUUID().toString());
 @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("spring.datasource.url",PG::getJdbcUrl);r.add("spring.datasource.username",PG::getUsername);r.add("spring.datasource.password",PG::getPassword);r.add("spring.jpa.hibernate.ddl-auto",()->"validate");r.add("spring.flyway.enabled",()->"true");r.add("springdoc.api-docs.enabled",()->"false");r.add("springdoc.swagger-ui.enabled",()->"false");}
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired IngredientRepository ingredients; @Autowired MouvementStockRepository mouvements; @Autowired ProduitIngredientRepository recettes; @Autowired ProduitRepository produits; @Autowired CategorieRepository categories; @Autowired EmployeRepository employes; @Autowired UtilisateurRepository utilisateurs; @Autowired TableRestaurantRepository tables; @Autowired CommandeRepository commandes; @Autowired RoleRepository roles;

 @Test void permissionsSontAttribuees() {assertThat(roles.findWithPermissionsByNom(NomRole.RESPONSABLE_STOCK).orElseThrow().getPermissions()).extracting(p->p.getNom()).contains(NomPermission.STOCK_GERER,NomPermission.STOCK_CONSULTER).doesNotContain(NomPermission.STOCK_NEGATIF_AUTORISER);}
 @Test void autorisationInsuffisanteEstRefusee() throws Exception {mvc.perform(get("/api/stock/ingredients")).andExpect(status().isUnauthorized());mvc.perform(get("/api/stock/ingredients").with(user(principal(-1L,"PRODUIT_CONSULTER")))).andExpect(status().isForbidden());mvc.perform(get("/api/stock/mouvements").with(user(principal(-1L,"PRODUIT_CONSULTER")))).andExpect(status().isForbidden());mvc.perform(delete("/api/stock/produits/1/recette/1").param("version","0").with(user(principal(-1L,"STOCK_CONSULTER"))).with(csrf())).andExpect(status().isForbidden());}

 @Test void historiqueDesMouvementsEstPagineEtFiltrable() throws Exception {Contexte c=contexte();Ingredient i=ingredient("Historique",BigDecimal.ZERO);mouvement(c,i,"ENTREE","8",null,UUID.randomUUID()).andExpect(status().isOk());mouvement(c,i,"PERTE","2","Casse",UUID.randomUUID()).andExpect(status().isOk());
  mvc.perform(get("/api/stock/mouvements").with(user(c.principal())).param("ingredientId",i.getId().toString()).param("type","PERTE").param("page","0").param("taille","10"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.contenu[0].ingredientId").value(i.getId())).andExpect(jsonPath("$.contenu[0].ingredient").value(i.getNom())).andExpect(jsonPath("$.contenu[0].type").value("PERTE")).andExpect(jsonPath("$.contenu[0].variation").value(-2.0)).andExpect(jsonPath("$.contenu[0].quantiteApres").value(6.0)).andExpect(jsonPath("$.contenu[0].motif").value("Casse")).andExpect(jsonPath("$.contenu[0].cleIdempotence").doesNotExist());
  mvc.perform(get("/api/stock/mouvements").with(user(c.principal())).param("taille","101")).andExpect(status().isBadRequest());
 }

 @Test void ingredientPeutEtreRetireDuneRecetteSansEffacerHistorique() throws Exception {Contexte c=contexte();Ingredient i=ingredient("Recette",BigDecimal.ZERO);mouvement(c,i,"ENTREE","5",null,UUID.randomUUID()).andExpect(status().isOk());long historiqueAvant=mouvements.count();Categorie cat=categories.saveAndFlush(new Categorie("Recette-"+UUID.randomUUID(),null));Produit p=produits.saveAndFlush(new Produit("Produit-"+UUID.randomUUID(),null,new BigDecimal("9"),cat));ProduitIngredient association=recettes.saveAndFlush(new ProduitIngredient(p,i,new BigDecimal("0.500")));
  mvc.perform(delete("/api/stock/produits/{produitId}/recette/{ingredientId}",p.getId(),i.getId()).param("version",String.valueOf(association.getVersion()+1)).with(user(c.principal())).with(csrf())).andExpect(status().isConflict());
  assertThat(recettes.findByProduitIdAndIngredientId(p.getId(),i.getId())).isPresent();
  mvc.perform(delete("/api/stock/produits/{produitId}/recette/{ingredientId}",p.getId(),i.getId()).param("version",String.valueOf(association.getVersion())).with(user(c.principal()))).andExpect(status().isForbidden());
  mvc.perform(delete("/api/stock/produits/{produitId}/recette/{ingredientId}",p.getId(),i.getId()).param("version",String.valueOf(association.getVersion())).with(user(c.principal())).with(csrf())).andExpect(status().isNoContent());
  assertThat(recettes.findByProduitIdAndIngredientId(p.getId(),i.getId())).isEmpty();assertThat(mouvements.count()).isEqualTo(historiqueAvant);
  mvc.perform(get("/api/stock/produits/{produitId}/recette",p.getId()).with(user(c.principal()))).andExpect(status().isOk()).andExpect(content().json("[]"));
 }

 @Test void entreeSortieRetourEtAlerteSontCalcules() throws Exception {Contexte c=contexte();Ingredient i=ingredient("Farine",new BigDecimal("5"));
  mouvement(c,i,"ENTREE","10",null,UUID.randomUUID()).andExpect(status().isOk()).andExpect(jsonPath("$.quantiteApres").value(10.0));
  mouvement(c,i,"SORTIE","4",null,UUID.randomUUID()).andExpect(status().isOk()).andExpect(jsonPath("$.quantiteApres").value(6.0));
  mouvement(c,i,"RETOUR","1",null,UUID.randomUUID()).andExpect(status().isOk()).andExpect(jsonPath("$.quantiteApres").value(7.0));
  mouvement(c,i,"PERTE","2","Périmé",UUID.randomUUID()).andExpect(status().isOk()).andExpect(jsonPath("$.quantiteApres").value(5.0));
  mvc.perform(get("/api/stock/ingredients").with(user(c.principal()))).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == %d)].stockFaible".formatted(i.getId())).value(true));
 }
 @Test void stockInsuffisantEtMotifManquantSontRefuses() throws Exception {Contexte c=contexte();Ingredient i=ingredient("Huile",BigDecimal.ONE);
  mouvement(c,i,"SORTIE","1",null,UUID.randomUUID()).andExpect(status().isConflict());
  mouvement(c,i,"PERTE","1",null,UUID.randomUUID()).andExpect(status().isConflict());
  mouvement(c,i,"AJUSTEMENT","1",null,UUID.randomUUID()).andExpect(status().isConflict());
 }
 @Test void quantitesFractionnairesSontRefuseesPourUneUnite() throws Exception {Contexte c=contexte();Ingredient i=ingredients.saveAndFlush(new Ingredient("Oeufs-"+UUID.randomUUID(),UniteMesure.UNITE,new BigDecimal("10")));
  mouvement(c,i,"ENTREE","1.5",null,UUID.randomUUID()).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Une quantité mesurée à l’unité doit être un nombre entier."));
  mvc.perform(post("/api/stock/ingredients").with(user(c.principal())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"Unitaire-"+UUID.randomUUID()+"\",\"unite\":\"UNITE\",\"seuilAlerte\":1.5}"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Une quantité mesurée à l’unité doit être un nombre entier."));
  Categorie cat=categories.saveAndFlush(new Categorie("Unite-"+UUID.randomUUID(),null));Produit p=produits.saveAndFlush(new Produit("Produit-unitaire-"+UUID.randomUUID(),null,new BigDecimal("12"),cat));
  mvc.perform(put("/api/stock/produits/{produitId}/recette",p.getId()).with(user(c.principal())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"ingredientId\":"+i.getId()+",\"quantite\":0.5}"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Une quantité mesurée à l’unité doit être un nombre entier."));
 }
 @Test void doubleSoumissionEstIdempotente() throws Exception {Contexte c=contexte();Ingredient i=ingredient("Sel",BigDecimal.ZERO);UUID key=UUID.randomUUID();long avant=mouvements.count();MvcResult a=mouvement(c,i,"ENTREE","3",null,key).andReturn();MvcResult b=mouvement(c,i,"ENTREE","3",null,key).andReturn();assertThat(node(a).path("id").asLong()).isEqualTo(node(b).path("id").asLong());assertThat(mouvements.count()).isEqualTo(avant+1);assertThat(ingredients.findById(i.getId()).orElseThrow().getQuantiteDisponible()).isEqualByComparingTo("3");}
 @Test void sortiesConcurrentesSontAtomiques() throws Exception {Contexte c=contexte();Ingredient i=ingredient("Sucre",BigDecimal.ZERO);mouvement(c,i,"ENTREE","10",null,UUID.randomUUID()).andExpect(status().isOk());ExecutorService ex=Executors.newFixedThreadPool(2);Future<Integer>a=ex.submit(()->mouvement(c,i,"SORTIE","7",null,UUID.randomUUID()).andReturn().getResponse().getStatus());Future<Integer>b=ex.submit(()->mouvement(c,i,"SORTIE","7",null,UUID.randomUUID()).andReturn().getResponse().getStatus());assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);ex.shutdownNow();assertThat(ingredients.findById(i.getId()).orElseThrow().getQuantiteDisponible()).isEqualByComparingTo("3");}
 @Test void confirmationDeduitUneSeuleFoisSelonLaRecette() throws Exception {Contexte c=contexte();Ingredient i=ingredient("Tomate",BigDecimal.ZERO);mouvement(c,i,"ENTREE","10",null,UUID.randomUUID()).andExpect(status().isOk());Categorie cat=categories.saveAndFlush(new Categorie("Stock-"+UUID.randomUUID(),null));Produit p=produits.saveAndFlush(new Produit("Plat-"+UUID.randomUUID(),null,new BigDecimal("12"),cat));recettes.saveAndFlush(new ProduitIngredient(p,i,new BigDecimal("2")));TableRestaurant t=tables.saveAndFlush(new TableRestaurant(TABLES.incrementAndGet(),4));Commande cmd=new Commande("CMD-STOCK-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(),t,null,c.utilisateur());cmd.ajouterLigne(p,3,p.getPrix(),null);cmd=commandes.saveAndFlush(cmd);
  mvc.perform(post("/api/commandes/{id}/confirmation",cmd.getId()).with(user(c.principalCommande())).with(csrf())).andExpect(status().isOk());
  mvc.perform(post("/api/commandes/{id}/confirmation",cmd.getId()).with(user(c.principalCommande())).with(csrf())).andExpect(status().isConflict());
  assertThat(ingredients.findById(i.getId()).orElseThrow().getQuantiteDisponible()).isEqualByComparingTo("4");assertThat(mouvements.existsByCommandeIdAndIngredientId(cmd.getId(),i.getId())).isTrue();
 }
 private Ingredient ingredient(String base,BigDecimal seuil){return ingredients.saveAndFlush(new Ingredient(base+UUID.randomUUID(),UniteMesure.KILOGRAMME,seuil));}
 private ResultActions mouvement(Contexte c,Ingredient i,String type,String q,String motif,UUID key)throws Exception{return mvc.perform(post("/api/stock/mouvements").with(user(c.principal())).with(csrf()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content("{\"ingredientId\":"+i.getId()+",\"type\":\""+type+"\",\"quantite\":"+q+(motif==null?"":",\"motif\":\""+motif+"\"")+"}"));}
 private Contexte contexte(){String s=UUID.randomUUID().toString().substring(0,8);Employe e=employes.saveAndFlush(new Employe("STK-"+s,"Stock",s,"Stock",LocalDate.now()));Utilisateur u=utilisateurs.saveAndFlush(new Utilisateur("stk-"+s+"@example.com","$2a$12$test",e));return new Contexte(u,principal(u.getId(),"STOCK_GERER","STOCK_CONSULTER"),principal(u.getId(),"STOCK_GERER","STOCK_CONSULTER","COMMANDE_GERER","ROLE_SERVEUR"));}
 private RestaurantUserPrincipal principal(Long id,String...a){return new RestaurantUserPrincipal(id,"x@x.test",null,true,Arrays.stream(a).map(SimpleGrantedAuthority::new).toList());}
 private JsonNode node(MvcResult r)throws Exception{return json.readTree(r.getResponse().getContentAsByteArray());}
 private record Contexte(Utilisateur utilisateur,RestaurantUserPrincipal principal,RestaurantUserPrincipal principalCommande){}
}
