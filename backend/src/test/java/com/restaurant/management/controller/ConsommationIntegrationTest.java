package com.restaurant.management.controller;

import com.restaurant.management.entity.Categorie;
import com.restaurant.management.entity.Commande;
import com.restaurant.management.entity.Employe;
import com.restaurant.management.entity.Ingredient;
import com.restaurant.management.entity.Produit;
import com.restaurant.management.entity.ProduitIngredient;
import com.restaurant.management.entity.TableRestaurant;
import com.restaurant.management.entity.Utilisateur;
import com.restaurant.management.enums.UniteMesure;
import com.restaurant.management.repository.CategorieRepository;
import com.restaurant.management.repository.CommandeRepository;
import com.restaurant.management.repository.EmployeRepository;
import com.restaurant.management.repository.IngredientRepository;
import com.restaurant.management.repository.ProduitIngredientRepository;
import com.restaurant.management.repository.ProduitRepository;
import com.restaurant.management.repository.TableRestaurantRepository;
import com.restaurant.management.repository.UtilisateurRepository;
import com.restaurant.management.security.RestaurantUserPrincipal;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConsommationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("restaurant_consommations_integration")
            .withUsername("restaurant_test")
            .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("springdoc.api-docs.enabled", () -> "false");
        registry.add("springdoc.swagger-ui.enabled", () -> "false");
    }

    @Autowired MockMvc mockMvc;
    @Autowired EmployeRepository employes;
    @Autowired UtilisateurRepository utilisateurs;
    @Autowired TableRestaurantRepository tables;
    @Autowired CategorieRepository categories;
    @Autowired ProduitRepository produits;
    @Autowired IngredientRepository ingredients;
    @Autowired ProduitIngredientRepository recettes;
    @Autowired CommandeRepository commandes;

    @Test
    void calculeProduitsEtIngredientsEtExclutBrouillonsEtAnnulations() throws Exception {
        Contexte contexte = contexte();
        Commande valide = commande(contexte, "VALIDE");
        valide.ajouterLigne(contexte.produitA(), 10, contexte.produitA().getPrix(), null);
        valide.ajouterLigne(contexte.produitB(), 4, contexte.produitB().getPrix(), null);
        valide.confirmer();
        commandes.saveAndFlush(valide);

        Commande annulee = commande(contexte, "ANNULEE");
        annulee.ajouterLigne(contexte.produitA(), 50, contexte.produitA().getPrix(), null);
        annulee.confirmer();
        annulee.annuler();
        commandes.saveAndFlush(annulee);

        Commande brouillon = commande(contexte, "BROUILLON");
        brouillon.ajouterLigne(contexte.produitB(), 50, contexte.produitB().getPrix(), null);
        commandes.saveAndFlush(brouillon);

        mockMvc.perform(get("/api/consommations")
                        .param("debut", aujourdHui())
                        .param("fin", aujourdHui())
                        .with(user(gerant())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debut").value(aujourdHui()))
                .andExpect(jsonPath("$.fin").value(aujourdHui()))
                .andExpect(jsonPath("$.produits[?(@.produitId == %d)].quantite"
                        .formatted(contexte.produitA().getId())).value(10))
                .andExpect(jsonPath("$.produits[?(@.produitId == %d)].quantite"
                        .formatted(contexte.produitB().getId())).value(4))
                .andExpect(jsonPath("$.ingredients[?(@.ingredientId == %d)].quantite"
                        .formatted(contexte.ingredientPartage().getId())).value(1.200))
                .andExpect(jsonPath("$.ingredients[?(@.ingredientId == %d)].quantite"
                        .formatted(contexte.ingredientA().getId())).value(0.250))
                .andExpect(jsonPath("$.ingredients[?(@.ingredientId == %d)].unite"
                        .formatted(contexte.ingredientPartage().getId())).value("KILOGRAMME"));
    }

    @Test
    void additionneUnProduitPresentDansPlusieursCommandes() throws Exception {
        Contexte contexte = contexte();
        Commande premiere = commande(contexte, "PREMIERE");
        premiere.ajouterLigne(contexte.produitA(), 2, contexte.produitA().getPrix(), null);
        premiere.confirmer();
        commandes.saveAndFlush(premiere);
        Commande seconde = commande(contexte, "SECONDE");
        seconde.ajouterLigne(contexte.produitA(), 3, contexte.produitA().getPrix(), null);
        seconde.confirmer();
        commandes.saveAndFlush(seconde);

        mockMvc.perform(get("/api/consommations")
                        .param("debut", aujourdHui())
                        .param("fin", aujourdHui())
                        .with(user(administrateur())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produits[?(@.produitId == %d)].quantite"
                        .formatted(contexte.produitA().getId())).value(5));
    }

    @Test
    void valideDatesObligatoiresEgalesInvalidesEtPeriodeSansDonnees() throws Exception {
        mockMvc.perform(get("/api/consommations").with(user(gerant())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/consommations")
                        .param("debut", aujourdHui())
                        .with(user(gerant())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/consommations")
                        .param("debut", "date-invalide")
                        .param("fin", aujourdHui())
                        .with(user(gerant())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/consommations")
                        .param("debut", "2026-02-02")
                        .param("fin", "2026-02-01")
                        .with(user(gerant())))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/consommations/rapport.pdf")
                        .param("debut", "2026-02-02")
                        .param("fin", "2026-02-01")
                        .with(user(gerant())))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/consommations")
                        .param("debut", "2024-01-01")
                        .param("fin", "2026-01-01")
                        .with(user(gerant())))
                .andExpect(status().isConflict());

        String demain = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();
        mockMvc.perform(get("/api/consommations")
                        .param("debut", demain)
                        .param("fin", demain)
                        .with(user(gerant())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produits").isEmpty())
                .andExpect(jsonPath("$.ingredients").isEmpty());
    }

    @Test
    void endpointsEtPdfSontReservesAuxAdministrateursEtGerants() throws Exception {
        String chemin = "/api/consommations?debut=" + aujourdHui() + "&fin=" + aujourdHui();
        String cheminPdf = "/api/consommations/rapport.pdf?debut=" + aujourdHui() + "&fin=" + aujourdHui();

        mockMvc.perform(get(chemin)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(cheminPdf)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(chemin).with(user(serveur()))).andExpect(status().isForbidden());
        mockMvc.perform(get(cheminPdf).with(user(serveur()))).andExpect(status().isForbidden());
        mockMvc.perform(get(chemin).with(user(gerant()))).andExpect(status().isOk());
        mockMvc.perform(get(chemin).with(user(administrateur()))).andExpect(status().isOk());
        mockMvc.perform(get(cheminPdf).with(user(gerant()))).andExpect(status().isOk());
        mockMvc.perform(get(cheminPdf).with(user(administrateur()))).andExpect(status().isOk());
    }

    @Test
    void pdfReprendLaMemePeriodeEtLesMemesDonneesEtGereLeCasVide() throws Exception {
        Contexte contexte = contexte();
        Commande commande = commande(contexte, "PDF");
        commande.ajouterLigne(contexte.produitA(), 10, contexte.produitA().getPrix(), null);
        commande.ajouterLigne(contexte.produitB(), 4, contexte.produitB().getPrix(), null);
        commande.confirmer();
        commandes.saveAndFlush(commande);

        MvcResult resultat = mockMvc.perform(get("/api/consommations/rapport.pdf")
                        .param("debut", aujourdHui())
                        .param("fin", aujourdHui())
                        .with(user(gerant())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString(
                                "rapport-consommation-" + aujourdHui() + "-au-" + aujourdHui() + ".pdf")))
                .andReturn();

        byte[] pdf = resultat.getResponse().getContentAsByteArray();
        assertThat(pdf).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String texte = new PDFTextStripper().getText(document);
            assertThat(texte)
                    .contains("RAPPORT DE CONSOMMATION")
                    .contains(contexte.produitA().getNom())
                    .contains(contexte.produitB().getNom())
                    .contains(contexte.ingredientPartage().getNom())
                    .contains("1,200")
                    .contains("du " + dateFrancaise() + " au " + dateFrancaise());
        }

        String demain = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();
        MvcResult vide = mockMvc.perform(get("/api/consommations/rapport.pdf")
                        .param("debut", demain)
                        .param("fin", demain)
                        .with(user(administrateur())))
                .andExpect(status().isOk())
                .andReturn();
        try (PDDocument document = Loader.loadPDF(vide.getResponse().getContentAsByteArray())) {
            assertThat(new PDFTextStripper().getText(document))
                    .contains("Aucune consommation enregistrée pour cette période.");
        }
    }

    private Contexte contexte() {
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        Employe employe = employes.saveAndFlush(new Employe(
                "CON-" + suffixe, "Gestion", suffixe, "Gérant", LocalDate.now()
        ));
        Utilisateur utilisateur = utilisateurs.saveAndFlush(new Utilisateur(
                "consommation-" + suffixe + "@example.test", "$2a$12$test", employe
        ));
        TableRestaurant table = tables.saveAndFlush(
                new TableRestaurant(Math.abs(suffixe.hashCode()), 4)
        );
        Categorie categorie = categories.saveAndFlush(new Categorie("Cons-" + suffixe, null));
        Produit produitA = produits.saveAndFlush(new Produit(
                "Produit-A-" + suffixe, null, new BigDecimal("10.00"), categorie
        ));
        Produit produitB = produits.saveAndFlush(new Produit(
                "Produit-B-" + suffixe, null, new BigDecimal("12.00"), categorie
        ));
        Ingredient ingredientPartage = ingredients.saveAndFlush(new Ingredient(
                "Ingredient-X-" + suffixe, UniteMesure.KILOGRAMME, BigDecimal.ZERO
        ));
        Ingredient ingredientA = ingredients.saveAndFlush(new Ingredient(
                "Ingredient-A-" + suffixe, UniteMesure.KILOGRAMME, BigDecimal.ZERO
        ));
        recettes.saveAndFlush(new ProduitIngredient(
                produitA, ingredientPartage, new BigDecimal("0.100")
        ));
        recettes.saveAndFlush(new ProduitIngredient(
                produitB, ingredientPartage, new BigDecimal("0.050")
        ));
        recettes.saveAndFlush(new ProduitIngredient(
                produitA, ingredientA, new BigDecimal("0.025")
        ));
        return new Contexte(utilisateur, table, produitA, produitB, ingredientPartage, ingredientA);
    }

    private Commande commande(Contexte contexte, String repere) {
        return new Commande(
                "CMD-CONS-" + repere + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                contexte.table(),
                null,
                contexte.utilisateur()
        );
    }

    private RestaurantUserPrincipal gerant() {
        return principal("ROLE_GERANT");
    }

    private RestaurantUserPrincipal administrateur() {
        return principal("ROLE_ADMINISTRATEUR");
    }

    private RestaurantUserPrincipal serveur() {
        return principal("ROLE_SERVEUR");
    }

    private RestaurantUserPrincipal principal(String... autorites) {
        return new RestaurantUserPrincipal(
                -1L,
                "test@example.test",
                null,
                true,
                Arrays.stream(autorites).map(SimpleGrantedAuthority::new).toList()
        );
    }

    private String aujourdHui() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    private String dateFrancaise() {
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        return "%02d/%02d/%04d".formatted(date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }

    private record Contexte(
            Utilisateur utilisateur,
            TableRestaurant table,
            Produit produitA,
            Produit produitB,
            Ingredient ingredientPartage,
            Ingredient ingredientA
    ) {
    }
}
