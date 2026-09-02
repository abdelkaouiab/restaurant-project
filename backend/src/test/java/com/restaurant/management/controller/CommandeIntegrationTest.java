package com.restaurant.management.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.management.entity.Categorie;
import com.restaurant.management.entity.Commande;
import com.restaurant.management.entity.Employe;
import com.restaurant.management.entity.Produit;
import com.restaurant.management.entity.Role;
import com.restaurant.management.entity.TableRestaurant;
import com.restaurant.management.entity.Utilisateur;
import com.restaurant.management.enums.NomPermission;
import com.restaurant.management.enums.NomRole;
import com.restaurant.management.enums.StatutCommande;
import com.restaurant.management.repository.CategorieRepository;
import com.restaurant.management.repository.CommandeRepository;
import com.restaurant.management.repository.EmployeRepository;
import com.restaurant.management.repository.ProduitRepository;
import com.restaurant.management.repository.RoleRepository;
import com.restaurant.management.repository.TableRestaurantRepository;
import com.restaurant.management.repository.UtilisateurRepository;
import com.restaurant.management.security.RestaurantUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommandeIntegrationTest {

    private static final AtomicInteger TABLE_SEQUENCE = new AtomicInteger(10_000);

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("restaurant_commandes_integration")
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
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void migrationAttribueLesPermissionsCommandeAuxRolesPrevus() {
        Role serveur = roleRepository.findWithPermissionsByNom(NomRole.SERVEUR).orElseThrow();
        Role caissier = roleRepository.findWithPermissionsByNom(NomRole.CAISSIER).orElseThrow();

        assertThat(serveur.getPermissions()).extracting(permission -> permission.getNom())
                .contains(NomPermission.COMMANDE_CONSULTER, NomPermission.COMMANDE_GERER);
        assertThat(caissier.getPermissions()).extracting(permission -> permission.getNom())
                .contains(NomPermission.COMMANDE_CONSULTER)
                .doesNotContain(NomPermission.COMMANDE_GERER);
    }

    @Test
    void fauxPrixEtTotauxSontRejetes() throws Exception {
        Contexte contexte = contexte();

        mockMvc.perform(post("/api/commandes")
                        .with(user(contexte.principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tableId":%d,"total":0.01}
                                """.formatted(contexte.table().getId())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/commandes")
                        .with(user(contexte.principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tableId":%d,"grandTotal":0.01}
                                """.formatted(contexte.table().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        long commandeId = creerBrouillon(contexte);
        mockMvc.perform(post("/api/commandes/{id}/lignes", commandeId)
                        .with(user(contexte.principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"produitId":%d,"quantite":2,"prixUnitaire":0.01,"sousTotal":0.02}
                                """.formatted(contexte.produit().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void prixEstCopieEtMontantsSontRecalculesCoteServeur() throws Exception {
        Contexte contexte = contexteAvecPrix("10.50");
        long commandeId = creerBrouillon(contexte);

        MvcResult ajout = ajouterLigne(contexte, commandeId, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes[0].prixUnitaire").value(10.50))
                .andExpect(jsonPath("$.lignes[0].sousTotal").value(21.00))
                .andExpect(jsonPath("$.montantTotal").value(21.00))
                .andReturn();
        assertThat(lire(ajout).path("lignes").get(0).path("id").asLong()).isPositive();

        contexte.produit().modifier(
                contexte.produit().getNom(), null, new BigDecimal("99.00"), contexte.categorie()
        );
        produitRepository.saveAndFlush(contexte.produit());

        mockMvc.perform(post("/api/commandes/{id}/confirmation", commandeId)
                        .with(user(contexte.principal())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("CONFIRMEE"))
                .andExpect(jsonPath("$.lignes[0].prixUnitaire").value(10.50))
                .andExpect(jsonPath("$.montantTotal").value(21.00));
    }

    @Test
    void commandeVideNePeutPasEtreConfirmee() throws Exception {
        Contexte contexte = contexte();
        long commandeId = creerBrouillon(contexte);

        mockMvc.perform(post("/api/commandes/{id}/confirmation", commandeId)
                        .with(user(contexte.principal())).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"));
    }

    @Test
    void produitIndisponibleEstRefuseALAjoutEtALaConfirmation() throws Exception {
        Contexte indisponible = contexte();
        indisponible.produit().modifier(
                indisponible.produit().getNom(), null, indisponible.produit().getPrix(),
                indisponible.categorie(), false
        );
        produitRepository.saveAndFlush(indisponible.produit());
        long commandeId = creerBrouillon(indisponible);

        mockMvc.perform(post("/api/commandes/{id}/lignes", commandeId)
                        .with(user(indisponible.principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"produitId":%d,"quantite":1}
                                """.formatted(indisponible.produit().getId())))
                .andExpect(status().isConflict());

        Contexte contexte = contexte();
        long autreCommande = creerBrouillon(contexte);
        ajouterLigne(contexte, autreCommande, 1).andExpect(status().isOk());
        contexte.produit().modifier(
                contexte.produit().getNom(), null, contexte.produit().getPrix(), contexte.categorie(), false
        );
        produitRepository.saveAndFlush(contexte.produit());

        mockMvc.perform(post("/api/commandes/{id}/confirmation", autreCommande)
                        .with(user(contexte.principal())).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void lignesSontModifiablesEtSupprimablesUniquementAvantConfirmation() throws Exception {
        Contexte contexte = contexte();
        long commandeId = creerBrouillon(contexte);
        JsonNode apresAjout = lire(ajouterLigne(contexte, commandeId, 1)
                .andExpect(status().isOk()).andReturn());
        long ligneId = apresAjout.path("lignes").get(0).path("id").asLong();

        mockMvc.perform(put("/api/commandes/{id}/lignes/{ligneId}", commandeId, ligneId)
                        .with(user(contexte.principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantite\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes[0].quantite").value(3));

        mockMvc.perform(post("/api/commandes/{id}/confirmation", commandeId)
                        .with(user(contexte.principal())).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/commandes/{id}/lignes/{ligneId}", commandeId, ligneId)
                        .with(user(contexte.principal())).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/commandes/{id}/confirmation", commandeId)
                        .with(user(contexte.principal())).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void annulationEstControleeEtTerminale() throws Exception {
        Contexte contexte = contexte();
        long commandeId = creerBrouillon(contexte);

        mockMvc.perform(post("/api/commandes/{id}/annulation", commandeId)
                        .with(user(contexte.principal())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ANNULEE"))
                .andExpect(jsonPath("$.annuleeLe").isNotEmpty());

        mockMvc.perform(post("/api/commandes/{id}/annulation", commandeId)
                        .with(user(contexte.principal())).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/commandes/{id}/lignes", commandeId)
                        .with(user(contexte.principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"produitId":%d,"quantite":1}
                                """.formatted(contexte.produit().getId())))
                .andExpect(status().isConflict());

        long commandeConfirmee = creerBrouillon(contexte);
        ajouterLigne(contexte, commandeConfirmee, 1).andExpect(status().isOk());
        mockMvc.perform(post("/api/commandes/{id}/confirmation", commandeConfirmee)
                        .with(user(contexte.principal())).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/commandes/{id}/annulation", commandeConfirmee)
                        .with(user(contexte.principal())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ANNULEE"));
    }

    @Test
    void autorisationsEtProprieteServeurSontVerifiees() throws Exception {
        Contexte proprietaire = contexte();
        long commandeId = creerBrouillon(proprietaire);
        Contexte autreServeur = contexte();

        mockMvc.perform(get("/api/commandes/{id}", commandeId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/commandes")
                        .with(user(principal(autreServeur.utilisateur().getId(), "PRODUIT_GERER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":" + autreServeur.table().getId() + "}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/commandes/{id}", commandeId)
                        .with(user(autreServeur.principal())))
                .andExpect(status().isForbidden());

        RestaurantUserPrincipal gerant = principal(
                autreServeur.utilisateur().getId(),
                "COMMANDE_CONSULTER", "ROLE_GERANT"
        );
        mockMvc.perform(get("/api/commandes/{id}", commandeId).with(user(gerant)))
                .andExpect(status().isOk());
    }

    @Test
    void verrouillageOptimisteRefuseUneModificationConcurrente() throws Exception {
        Contexte contexte = contexte();
        long commandeId = creerBrouillon(contexte);
        long ligneId = lire(ajouterLigne(contexte, commandeId, 1)
                .andExpect(status().isOk()).andReturn())
                .path("lignes").get(0).path("id").asLong();

        CyclicBarrier barriere = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Void> premier = executor.submit(() -> modifierConcurrent(
                commandeId, ligneId, 2, barriere
        ));
        Future<Void> second = executor.submit(() -> modifierConcurrent(
                commandeId, ligneId, 3, barriere
        ));

        int succes = 0;
        int conflits = 0;
        for (Future<Void> future : List.of(premier, second)) {
            try {
                future.get(10, TimeUnit.SECONDS);
                succes++;
            } catch (ExecutionException exception) {
                conflits++;
            }
        }
        executor.shutdownNow();

        assertThat(succes).isEqualTo(1);
        assertThat(conflits).isEqualTo(1);
        Commande rechargee = commandeRepository.findAvecDetailsById(commandeId).orElseThrow();
        assertThat(rechargee.getVersion()).isPositive();
        assertThat(rechargee.getLignes().getFirst().getQuantite()).isIn(2, 3);
    }

    private Void modifierConcurrent(
            long commandeId,
            long ligneId,
            int quantite,
            CyclicBarrier barriere
    ) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            Commande commande = commandeRepository.findAvecDetailsById(commandeId).orElseThrow();
            attendre(barriere);
            commande.modifierQuantite(ligneId, quantite);
            commandeRepository.flush();
        });
        return null;
    }

    private void attendre(CyclicBarrier barriere) {
        try {
            barriere.await(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private org.springframework.test.web.servlet.ResultActions ajouterLigne(
            Contexte contexte,
            long commandeId,
            int quantite
    ) throws Exception {
        return mockMvc.perform(post("/api/commandes/{id}/lignes", commandeId)
                .with(user(contexte.principal())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"produitId":%d,"quantite":%d}
                        """.formatted(contexte.produit().getId(), quantite)));
    }

    private long creerBrouillon(Contexte contexte) throws Exception {
        MvcResult resultat = mockMvc.perform(post("/api/commandes")
                        .with(user(contexte.principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":" + contexte.table().getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andReturn();
        return lire(resultat).path("id").asLong();
    }

    private Contexte contexte() {
        return contexteAvecPrix("15.00");
    }

    private Contexte contexteAvecPrix(String prix) {
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        Employe employe = employeRepository.saveAndFlush(new Employe(
                "CMD-" + suffixe, "Serveur", suffixe, "Service", LocalDate.now()
        ));
        Utilisateur utilisateur = new Utilisateur(
                "serveur-" + suffixe + "@example.com",
                passwordEncoder.encode("Test-only-password-42!"),
                employe
        );
        Role role = roleRepository.findByNom(NomRole.SERVEUR).orElseThrow();
        utilisateur.addRole(role);
        utilisateur = utilisateurRepository.saveAndFlush(utilisateur);
        TableRestaurant table = tableRepository.saveAndFlush(
                new TableRestaurant(TABLE_SEQUENCE.incrementAndGet(), 4)
        );
        Categorie categorie = categorieRepository.saveAndFlush(new Categorie("Cat-" + suffixe, null));
        Produit produit = produitRepository.saveAndFlush(
                new Produit("Produit-" + suffixe, null, new BigDecimal(prix), categorie)
        );
        return new Contexte(
                utilisateur,
                table,
                categorie,
                produit,
                principal(
                        utilisateur.getId(),
                        "COMMANDE_CONSULTER", "COMMANDE_GERER", "ROLE_SERVEUR"
                )
        );
    }

    private RestaurantUserPrincipal principal(Long id, String... autorites) {
        return new RestaurantUserPrincipal(
                id,
                "principal-" + id + "@example.com",
                null,
                true,
                java.util.Arrays.stream(autorites).map(SimpleGrantedAuthority::new).toList()
        );
    }

    private JsonNode lire(MvcResult resultat) throws Exception {
        return objectMapper.readTree(resultat.getResponse().getContentAsByteArray());
    }

    private record Contexte(
            Utilisateur utilisateur,
            TableRestaurant table,
            Categorie categorie,
            Produit produit,
            RestaurantUserPrincipal principal
    ) {
    }
}
