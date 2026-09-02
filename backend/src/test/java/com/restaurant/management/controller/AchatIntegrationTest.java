package com.restaurant.management.controller;

import com.fasterxml.jackson.databind.*;
import com.restaurant.management.entity.*;
import com.restaurant.management.enums.*;
import com.restaurant.management.repository.*;
import com.restaurant.management.security.RestaurantUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers(disabledWithoutDocker=true) @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class AchatIntegrationTest {
 @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("restaurant_achats_integration").withUsername("test").withPassword(UUID.randomUUID().toString());
 @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("spring.datasource.url",PG::getJdbcUrl);r.add("spring.datasource.username",PG::getUsername);r.add("spring.datasource.password",PG::getPassword);r.add("spring.jpa.hibernate.ddl-auto",()->"validate");r.add("spring.flyway.enabled",()->"true");r.add("springdoc.api-docs.enabled",()->"false");r.add("springdoc.swagger-ui.enabled",()->"false");}
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired EmployeRepository employes; @Autowired UtilisateurRepository utilisateurs; @Autowired FournisseurRepository fournisseurs; @Autowired IngredientRepository ingredients; @Autowired AchatRepository achats; @Autowired MouvementStockRepository mouvements; @Autowired RoleRepository roles; @Autowired JournalActiviteRepository journaux;

 @Test void permissionsEtAutorisationsSontAppliquees() throws Exception {
  assertThat(roles.findWithPermissionsByNom(NomRole.RESPONSABLE_STOCK).orElseThrow().getPermissions()).extracting(Permission::getNom).contains(NomPermission.ACHAT_CONSULTER,NomPermission.ACHAT_GERER);
  mvc.perform(get("/api/achats")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/achats/1/receptions")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/achats").with(user(principal(-1L,"STOCK_CONSULTER")))).andExpect(status().isForbidden());
  mvc.perform(get("/api/achats/1/receptions").with(user(principal(-1L,"STOCK_CONSULTER")))).andExpect(status().isForbidden());
  mvc.perform(patch("/api/achats/fournisseurs/1/statut").with(user(principal(-1L,"ACHAT_CONSULTER"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"actif\":false,\"version\":0}")).andExpect(status().isForbidden());
 }
 @Test void fournisseurPeutEtreDesactiveEtReactiveAvecControleDeVersion() throws Exception {Contexte c=contexte();Fournisseur f=fournisseurs.saveAndFlush(new Fournisseur("Statut-"+UUID.randomUUID(),null,null,null));
  mvc.perform(patch("/api/achats/fournisseurs/{id}/statut",f.getId()).with(user(c.principal())).contentType(MediaType.APPLICATION_JSON).content("{\"actif\":false,\"version\":0}")).andExpect(status().isForbidden());
  mvc.perform(patch("/api/achats/fournisseurs/{id}/statut",f.getId()).with(user(c.principal())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"actif\":false,\"version\":1}")).andExpect(status().isConflict());
  mvc.perform(patch("/api/achats/fournisseurs/{id}/statut",f.getId()).with(user(c.principal())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"actif\":false,\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(false)).andExpect(jsonPath("$.version").value(1));
  mvc.perform(post("/api/achats").with(user(c.principal())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"fournisseurId\":"+f.getId()+"}")).andExpect(status().isNotFound());
  mvc.perform(patch("/api/achats/fournisseurs/{id}/statut",f.getId()).with(user(c.principal())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"actif\":true,\"version\":1}")).andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(true)).andExpect(jsonPath("$.version").value(2));
  assertThat(journaux.findAll()).extracting(JournalActivite::getAction).contains("FOURNISSEUR_DESACTIVATION","FOURNISSEUR_REACTIVATION");
 }
 @Test void totalEstRecalculeEtChampsCalculablesSontRefuses() throws Exception {Contexte c=contexte();Ingredient i=ingredient("Farine");long achat=creer(c);mvc.perform(post("/api/achats/{id}/lignes",achat).with(user(c.principal())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"ingredientId\":"+i.getId()+",\"quantite\":3,\"prixUnitaire\":2.50}" )).andExpect(status().isOk()).andExpect(jsonPath("$.lignes[0].sousTotal").value(7.50)).andExpect(jsonPath("$.montantTotal").value(7.50));mvc.perform(post("/api/achats/{id}/lignes",achat).with(user(c.principal())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"ingredientId\":"+i.getId()+",\"quantite\":1,\"prixUnitaire\":1,\"sousTotal\":0.01}" )).andExpect(status().isBadRequest());}
 @Test void receptionCompleteCreeUneEntreeStock() throws Exception {Contexte c=contexte();Scenario s=scenario(c,"Tomate","10","2");commander(c,s.achat());recevoir(c,s,"10",UUID.randomUUID()).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("RECU")).andExpect(jsonPath("$.lignes[0].quantiteRecue").value(10.0));assertThat(stock(s.ingredient())).isEqualByComparingTo("10");assertThat(mouvements.count()).isPositive();}
 @Test void achatEtReceptionFractionnairesSontRefusesPourUneUnite() throws Exception {Contexte c=contexte();Ingredient i=ingredients.saveAndFlush(new Ingredient("Oeufs-"+UUID.randomUUID(),UniteMesure.UNITE,BigDecimal.ZERO));long achat=creer(c);
  mvc.perform(post("/api/achats/{id}/lignes",achat).with(user(c.principal())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"ingredientId\":"+i.getId()+",\"quantite\":2.5,\"prixUnitaire\":1}"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Une quantité mesurée à l’unité doit être un nombre entier."));
  MvcResult ligne=mvc.perform(post("/api/achats/{id}/lignes",achat).with(user(c.principal())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"ingredientId\":"+i.getId()+",\"quantite\":3,\"prixUnitaire\":1}"))
   .andExpect(status().isOk()).andReturn();long ligneId=node(ligne).path("lignes").get(0).path("id").asLong();commander(c,achat);
  Scenario scenario=new Scenario(achat,ligneId,i);recevoir(c,scenario,"1.5",UUID.randomUUID()).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Une quantité mesurée à l’unité doit être un nombre entier."));
  assertThat(stock(i)).isEqualByComparingTo("0");
 }
 @Test void receptionsPartiellesAboutissentAUneReceptionComplete() throws Exception {Contexte c=contexte();Scenario s=scenario(c,"Huile","10","3");commander(c,s.achat());recevoir(c,s,"4",UUID.randomUUID()).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("PARTIELLEMENT_RECU")).andExpect(jsonPath("$.lignes[0].quantiteRestante").value(6.0));recevoir(c,s,"6",UUID.randomUUID()).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("RECU"));assertThat(stock(s.ingredient())).isEqualByComparingTo("10");}
 @Test void historiqueExposeChaqueReceptionSansDonneesInternes() throws Exception {Contexte c=contexte();Scenario s=scenario(c,"Beurre","10","4");commander(c,s.achat());recevoir(c,s,"4",UUID.randomUUID()).andExpect(status().isOk());recevoir(c,s,"6",UUID.randomUUID()).andExpect(status().isOk());mvc.perform(get("/api/achats/{id}/receptions",s.achat()).with(user(c.principal()))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].lignes[0].ligneAchatId").value(s.ligne())).andExpect(jsonPath("$[0].lignes[0].ingredient").isNotEmpty()).andExpect(jsonPath("$[0].lignes[0].quantite").value(6.0)).andExpect(jsonPath("$[0].creeLe").isNotEmpty()).andExpect(jsonPath("$[0].cleIdempotence").doesNotExist()).andExpect(jsonPath("$[0].signatureRequete").doesNotExist());}
 @Test void doubleReceptionAvecMemeCleNaQuUnEffet() throws Exception {Contexte c=contexte();Scenario s=scenario(c,"Sel","5","1");commander(c,s.achat());UUID cle=UUID.randomUUID();recevoir(c,s,"5",cle).andExpect(status().isOk());recevoir(c,s,"5",cle).andExpect(status().isOk());assertThat(stock(s.ingredient())).isEqualByComparingTo("5");assertThat(achats.findById(s.achat()).orElseThrow().getStatut()).isEqualTo(StatutAchat.RECU);}
 @Test void receptionsConcurrentesNeDepassentPasLaCommande() throws Exception {Contexte c=contexte();Scenario s=scenario(c,"Sucre","10","1");commander(c,s.achat());ExecutorService ex=Executors.newFixedThreadPool(2);Future<Integer>a=ex.submit(()->recevoir(c,s,"6",UUID.randomUUID()).andReturn().getResponse().getStatus());Future<Integer>b=ex.submit(()->recevoir(c,s,"6",UUID.randomUUID()).andReturn().getResponse().getStatus());assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);ex.shutdownNow();assertThat(stock(s.ingredient())).isEqualByComparingTo("6");}
 @Test void transitionsInvalidesSontRefusees() throws Exception {Contexte c=contexte();Scenario s=scenario(c,"Poivre","2","4");recevoir(c,s,"1",UUID.randomUUID()).andExpect(status().isConflict());commander(c,s.achat());recevoir(c,s,"1",UUID.randomUUID()).andExpect(status().isOk());mvc.perform(post("/api/achats/{id}/annulation",s.achat()).with(user(c.principal())).with(csrf())).andExpect(status().isConflict());mvc.perform(post("/api/achats/{id}/lignes",s.achat()).with(user(c.principal())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"ingredientId\":"+s.ingredient().getId()+",\"quantite\":1,\"prixUnitaire\":1}" )).andExpect(status().isConflict());}

 private Scenario scenario(Contexte c,String nom,String quantite,String prix)throws Exception{Ingredient i=ingredient(nom);long a=creer(c);MvcResult r=mvc.perform(post("/api/achats/{id}/lignes",a).with(user(c.principal())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"ingredientId\":"+i.getId()+",\"quantite\":"+quantite+",\"prixUnitaire\":"+prix+"}" )).andExpect(status().isOk()).andReturn();return new Scenario(a,node(r).path("lignes").get(0).path("id").asLong(),i);}
 private long creer(Contexte c)throws Exception{Fournisseur f=fournisseurs.saveAndFlush(new Fournisseur("F-"+UUID.randomUUID(),null,null,null));MvcResult r=mvc.perform(post("/api/achats").with(user(c.principal())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"fournisseurId\":"+f.getId()+"}" )).andExpect(status().isCreated()).andReturn();return node(r).path("id").asLong();}
 private void commander(Contexte c,long id)throws Exception{mvc.perform(post("/api/achats/{id}/commande",id).with(user(c.principal())).with(csrf())).andExpect(status().isOk());}
 private ResultActions recevoir(Contexte c,Scenario s,String q,UUID cle)throws Exception{return mvc.perform(post("/api/achats/{id}/receptions",s.achat()).with(user(c.principal())).with(csrf()).header("Idempotency-Key",cle).contentType(MediaType.APPLICATION_JSON).content("{\"lignes\":[{\"ligneAchatId\":"+s.ligne()+",\"quantite\":"+q+"}]}"));}
 private Ingredient ingredient(String n){return ingredients.saveAndFlush(new Ingredient(n+"-"+UUID.randomUUID(),UniteMesure.KILOGRAMME,BigDecimal.ZERO));}
 private BigDecimal stock(Ingredient i){return ingredients.findById(i.getId()).orElseThrow().getQuantiteDisponible();}
 private Contexte contexte(){String s=UUID.randomUUID().toString().substring(0,8);Employe e=employes.saveAndFlush(new Employe("ACH-"+s,"Achats",s,"Achats",LocalDate.now()));Utilisateur u=utilisateurs.saveAndFlush(new Utilisateur("ach-"+s+"@example.com","$2a$12$test",e));return new Contexte(u,principal(u.getId(),"ACHAT_GERER","ACHAT_CONSULTER"));}
 private RestaurantUserPrincipal principal(Long id,String...a){return new RestaurantUserPrincipal(id,"x@x.test",null,true,Arrays.stream(a).map(SimpleGrantedAuthority::new).toList());}
 private JsonNode node(MvcResult r)throws Exception{return json.readTree(r.getResponse().getContentAsByteArray());}
 private record Contexte(Utilisateur utilisateur,RestaurantUserPrincipal principal){} private record Scenario(long achat,long ligne,Ingredient ingredient){}
}
