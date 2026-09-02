package com.restaurant.management.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.management.entity.Categorie;
import com.restaurant.management.entity.Commande;
import com.restaurant.management.entity.Employe;
import com.restaurant.management.entity.Produit;
import com.restaurant.management.entity.TableRestaurant;
import com.restaurant.management.entity.Utilisateur;
import com.restaurant.management.enums.NomPermission;
import com.restaurant.management.enums.NomRole;
import com.restaurant.management.enums.StatutCommande;
import com.restaurant.management.repository.CategorieRepository;
import com.restaurant.management.repository.CommandeRepository;
import com.restaurant.management.repository.EmployeRepository;
import com.restaurant.management.repository.FactureRepository;
import com.restaurant.management.repository.PaiementRepository;
import com.restaurant.management.repository.ProduitRepository;
import com.restaurant.management.repository.RoleRepository;
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
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
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
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaiementIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("restaurant_paiements_integration")
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
    @Autowired ObjectMapper objectMapper;
    @Autowired EmployeRepository employeRepository;
    @Autowired UtilisateurRepository utilisateurRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired TableRestaurantRepository tableRepository;
    @Autowired CategorieRepository categorieRepository;
    @Autowired ProduitRepository produitRepository;
    @Autowired CommandeRepository commandeRepository;
    @Autowired PaiementRepository paiementRepository;
    @Autowired FactureRepository factureRepository;

    @Test
    void permissionEstAttribueeUniquementAuxRolesDeCaissePrevus() {
        assertThat(roleRepository.findWithPermissionsByNom(NomRole.CAISSIER).orElseThrow().getPermissions())
                .extracting(permission -> permission.getNom())
                .contains(NomPermission.CAISSE_PAIEMENTS_GERER);
        assertThat(roleRepository.findWithPermissionsByNom(NomRole.SERVEUR).orElseThrow().getPermissions())
                .extracting(permission -> permission.getNom())
                .doesNotContain(NomPermission.CAISSE_PAIEMENTS_GERER);
    }

    @Test
    void autorisationEstControlee() throws Exception {
        mockMvc.perform(get("/api/caisse/commandes-a-payer"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/caisse/commandes-a-payer")
                        .with(user(principal(-1L, "COMMANDE_CONSULTER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/caisse/paiements"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/caisse/factures")
                        .with(user(principal(-1L, "COMMANDE_CONSULTER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/caisse/commandes/CMD-INTERDITE/facture.pdf")
                        .with(user(principal(-1L, "COMMANDE_CONSULTER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void consultationRetourneLesPaiementsDuJourEtLesFacturesRecentesSansCleIdempotence()
            throws Exception {
        Contexte contexte = contexte();
        payer(contexte, "20.00", UUID.randomUUID()).andExpect(status().isOk());

        mockMvc.perform(get("/api/caisse/paiements")
                        .param("date", LocalDate.now(java.time.ZoneOffset.UTC).toString())
                        .param("page", "0")
                        .param("taille", "5")
                        .with(user(contexte.principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.contenu[0].numeroCommande")
                        .value(contexte.commande().getNumero()))
                .andExpect(jsonPath("$.contenu[0].montant").value(20.00))
                .andExpect(jsonPath("$.contenu[0].mode").value("ESPECES"))
                .andExpect(jsonPath("$.contenu[0].cleIdempotence").doesNotExist());

        mockMvc.perform(get("/api/caisse/commandes/{numero}/paiements", contexte.commande().getNumero())
                        .param("page", "0")
                        .param("taille", "20")
                        .with(user(contexte.principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu[0].numeroCommande")
                        .value(contexte.commande().getNumero()))
                .andExpect(jsonPath("$.contenu[0].montant").value(20.00))
                .andExpect(jsonPath("$.contenu[0].cleIdempotence").doesNotExist());

        mockMvc.perform(get("/api/caisse/factures")
                        .param("page", "0")
                        .param("taille", "5")
                        .with(user(contexte.principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.contenu[0].numeroCommande")
                        .value(contexte.commande().getNumero()))
                .andExpect(jsonPath("$.contenu[0].montantTotal").value(20.00));
    }

    @Test
    void consultationValideLaPaginationEtLeFormatDeDate() throws Exception {
        Contexte contexte = contexte();
        mockMvc.perform(get("/api/caisse/paiements")
                        .param("taille", "0")
                        .with(user(contexte.principal())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/caisse/paiements")
                        .param("date", "16-08-2026")
                        .with(user(contexte.principal())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paiementsPartielsPuisCompletGenerentUneFactureEtMarquentLaCommandePayee() throws Exception {
        Contexte contexte = contexte();
        payer(contexte, "7.50", UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montantPaye").value(7.50))
                .andExpect(jsonPath("$.montantRestant").value(12.50))
                .andExpect(jsonPath("$.statutCommande").value("PRETE"))
                .andExpect(jsonPath("$.facture").doesNotExist());

        payer(contexte, "12.50", UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montantPaye").value(20.00))
                .andExpect(jsonPath("$.montantRestant").value(0.00))
                .andExpect(jsonPath("$.statutCommande").value("PAYEE"))
                .andExpect(jsonPath("$.facture.numero").isNotEmpty())
                .andExpect(jsonPath("$.facture.montantTotal").value(20.00));

        assertThat(factureRepository.findByCommandeId(contexte.commande().getId())).isPresent();
        assertThat(commandeRepository.findById(contexte.commande().getId()).orElseThrow().getStatut())
                .isEqualTo(StatutCommande.PAYEE);

        MvcResult resultatPdf = mockMvc.perform(
                        get("/api/caisse/commandes/{numero}/facture.pdf", contexte.commande().getNumero())
                                .with(user(contexte.principal())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andReturn();
        byte[] pdf = resultatPdf.getResponse().getContentAsByteArray();
        assertThat(pdf).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String texte = new PDFTextStripper().getText(document);
            assertThat(texte)
                    .contains(contexte.commande().getNumero())
                    .contains(contexte.commande().getLignes().getFirst().getProduit().getNom())
                    .contains("20,00 EUR");
        }
    }

    @Test
    void montantsInvalidesEtCalculsDuClientSontRefuses() throws Exception {
        Contexte contexte = contexte();
        payer(contexte, "0", UUID.randomUUID()).andExpect(status().isBadRequest());
        payer(contexte, "-1", UUID.randomUUID()).andExpect(status().isBadRequest());
        payer(contexte, "20.01", UUID.randomUUID()).andExpect(status().isConflict());

        mockMvc.perform(post("/api/caisse/commandes/{numero}/paiements", contexte.commande().getNumero())
                        .with(user(contexte.principal())).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"montant":10,"mode":"ESPECES","montantRestant":0,"total":1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void doubleSoumissionEstIdempotenteEtCleReutiliseeDifferemmentEstRefusee() throws Exception {
        Contexte contexte = contexte();
        UUID cle = UUID.randomUUID();
        long avant = paiementRepository.count();
        MvcResult premier = payer(contexte, "5.00", cle).andExpect(status().isOk()).andReturn();
        MvcResult second = payer(contexte, "5.00", cle).andExpect(status().isOk()).andReturn();
        assertThat(lire(premier).path("id").asLong()).isEqualTo(lire(second).path("id").asLong());
        assertThat(paiementRepository.count()).isEqualTo(avant + 1);

        payer(contexte, "6.00", cle).andExpect(status().isConflict());
    }

    @Test
    void facturePrematureeEstRefusee() throws Exception {
        Contexte contexte = contexte();
        mockMvc.perform(get("/api/caisse/commandes/{numero}/facture", contexte.commande().getNumero())
                        .with(user(contexte.principal())))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/caisse/commandes/{numero}/facture.pdf", contexte.commande().getNumero())
                        .with(user(contexte.principal())))
                .andExpect(status().isConflict());
    }

    @Test
    void paiementsConcurrentsNePeuventPasDepasserLeTotal() throws Exception {
        Contexte contexte = contexte();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Integer> premier = executor.submit(() -> payerConcurrent(contexte, UUID.randomUUID()));
        Future<Integer> second = executor.submit(() -> payerConcurrent(contexte, UUID.randomUUID()));

        assertThat(Arrays.asList(premier.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200, 409);
        executor.shutdownNow();
        assertThat(paiementRepository.sommeParCommandeEtStatut(
                contexte.commande().getId(),
                com.restaurant.management.enums.StatutPaiement.VALIDE
        )).isEqualByComparingTo("15.00");
    }

    private int payerConcurrent(Contexte contexte, UUID cle) throws Exception {
        return payer(contexte, "15.00", cle).andReturn().getResponse().getStatus();
    }

    private org.springframework.test.web.servlet.ResultActions payer(
            Contexte contexte,
            String montant,
            UUID cle
    ) throws Exception {
        return mockMvc.perform(post("/api/caisse/commandes/{numero}/paiements", contexte.commande().getNumero())
                .with(user(contexte.principal())).with(csrf())
                .header("Idempotency-Key", cle)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"montant":%s,"mode":"ESPECES"}
                        """.formatted(montant)));
    }

    private Contexte contexte() {
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        Employe employe = employeRepository.saveAndFlush(new Employe(
                "PAY-" + suffixe, "Caissier", suffixe, "Caisse", LocalDate.now()
        ));
        Utilisateur utilisateur = utilisateurRepository.saveAndFlush(new Utilisateur(
                "pay-" + suffixe + "@example.com", "$2a$12$hash.detestuniquement", employe
        ));
        TableRestaurant table = tableRepository.saveAndFlush(
                new TableRestaurant(Math.abs(suffixe.hashCode()), 4)
        );
        Categorie categorie = categorieRepository.saveAndFlush(new Categorie("Pay-" + suffixe, null));
        Produit produit = produitRepository.saveAndFlush(
                new Produit("Produit-" + suffixe, null, new BigDecimal("10.00"), categorie)
        );
        Commande commande = new Commande("CMD-" + suffixe.toUpperCase(), table, null, utilisateur);
        commande.ajouterLigne(produit, 2, produit.getPrix(), null);
        commande.confirmer();
        commande.demarrerPreparation();
        commande.marquerPrete();
        commande = commandeRepository.saveAndFlush(commande);
        return new Contexte(
                commande,
                principal(utilisateur.getId(), "CAISSE_PAIEMENTS_GERER", "ROLE_CAISSIER")
        );
    }

    private RestaurantUserPrincipal principal(Long id, String... autorites) {
        return new RestaurantUserPrincipal(
                id, "test-" + id + "@example.com", null, true,
                Arrays.stream(autorites).map(SimpleGrantedAuthority::new).toList()
        );
    }

    private JsonNode lire(MvcResult resultat) throws Exception {
        return objectMapper.readTree(resultat.getResponse().getContentAsByteArray());
    }

    private record Contexte(Commande commande, RestaurantUserPrincipal principal) {
    }
}
