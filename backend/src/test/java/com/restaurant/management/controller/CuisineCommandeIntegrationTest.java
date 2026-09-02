package com.restaurant.management.controller;

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
import com.restaurant.management.repository.NotificationInterneRepository;
import com.restaurant.management.repository.ProduitRepository;
import com.restaurant.management.repository.RoleRepository;
import com.restaurant.management.repository.TableRestaurantRepository;
import com.restaurant.management.repository.UtilisateurRepository;
import com.restaurant.management.security.RestaurantUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CuisineCommandeIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("restaurant_cuisine_integration")
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
    @Autowired EmployeRepository employeRepository;
    @Autowired UtilisateurRepository utilisateurRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired TableRestaurantRepository tableRepository;
    @Autowired CategorieRepository categorieRepository;
    @Autowired ProduitRepository produitRepository;
    @Autowired CommandeRepository commandeRepository;
    @Autowired NotificationInterneRepository notificationRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void migrationAttribueLaPermissionCuisineAuxRolesPrevus() {
        assertThat(roleRepository.findWithPermissionsByNom(NomRole.CUISINE).orElseThrow().getPermissions())
                .extracting(permission -> permission.getNom())
                .contains(NomPermission.CUISINE_COMMANDES_GERER);
        assertThat(roleRepository.findWithPermissionsByNom(NomRole.SERVEUR).orElseThrow().getPermissions())
                .extracting(permission -> permission.getNom())
                .doesNotContain(NomPermission.CUISINE_COMMANDES_GERER);
    }

    @Test
    void accesNonAuthentifieEtInterditSontRefuses() throws Exception {
        mockMvc.perform(get("/api/cuisine/commandes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/cuisine/commandes").with(user(principal("PRODUIT_GERER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listeEtTransitionsReussissentSansDonneesFinancieresOuPersonnelles() throws Exception {
        Commande commande = commandeConfirmee("sans-oignon");

        String corps = mockMvc.perform(get("/api/cuisine/commandes").with(user(principalCuisine())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numero == '%s')]".formatted(commande.getNumero())).exists())
                .andExpect(jsonPath("$[?(@.numero == '%s')].table".formatted(commande.getNumero()))
                        .value(commande.getTable().getNumero()))
                .andExpect(jsonPath("$[?(@.numero == '%s')].produits[0].quantite"
                        .formatted(commande.getNumero())).value(2))
                .andExpect(jsonPath("$[?(@.numero == '%s')].produits[0].remarque"
                        .formatted(commande.getNumero())).value("sans-oignon"))
                .andExpect(jsonPath("$[?(@.numero == '%s')].heure".formatted(commande.getNumero())).exists())
                .andExpect(jsonPath("$[?(@.numero == '%s')].etat".formatted(commande.getNumero()))
                        .value("CONFIRMEE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(corps).doesNotContain(
                "prix", "montant", "total", "client", "creePar", "email", "nomClient"
        );

        mockMvc.perform(post("/api/cuisine/commandes/{numero}/demarrage", commande.getNumero())
                        .with(user(principalCuisine())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("EN_PREPARATION"));
        mockMvc.perform(post("/api/cuisine/commandes/{numero}/prete", commande.getNumero())
                        .with(user(principalCuisine())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("PRETE"));

        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(commandeRepository.findAvecDetailsByNumero(commande.getNumero()).orElseThrow().getStatut())
                .isEqualTo(StatutCommande.PRETE);
    }

    @Test
    void commandesActivesEtHistoriqueCourtDesCommandesPretesSontListes() throws Exception {
        Commande visible = commandeConfirmee(null);
        Commande prete = commandeConfirmee(null);
        prete.demarrerPreparation();
        prete.marquerPrete();
        commandeRepository.saveAndFlush(prete);
        Commande preteAncienne = commandeConfirmee(null);
        preteAncienne.demarrerPreparation();
        preteAncienne.marquerPrete();
        commandeRepository.saveAndFlush(preteAncienne);
        jdbcTemplate.update(
                "update commandes set prete_le = ? where id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofHours(3))),
                preteAncienne.getId()
        );

        mockMvc.perform(get("/api/cuisine/commandes").with(user(principalCuisine())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numero == '%s')]".formatted(visible.getNumero())).exists())
                .andExpect(jsonPath("$[?(@.numero == '%s')]".formatted(prete.getNumero())).exists())
                .andExpect(jsonPath("$[?(@.numero == '%s')].etat".formatted(prete.getNumero()))
                        .value("PRETE"))
                .andExpect(jsonPath("$[?(@.numero == '%s')]".formatted(preteAncienne.getNumero()))
                        .doesNotExist());
    }

    @Test
    void transitionInvalideEstRefuseeEtCsrfResteObligatoire() throws Exception {
        Commande commande = commandeConfirmee(null);
        mockMvc.perform(post("/api/cuisine/commandes/{numero}/prete", commande.getNumero())
                        .with(user(principalCuisine())).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/cuisine/commandes/{numero}/demarrage", commande.getNumero())
                        .with(user(principalCuisine())))
                .andExpect(status().isForbidden());
    }

    private Commande commandeConfirmee(String remarque) {
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        Employe employe = employeRepository.saveAndFlush(new Employe(
                "KIT-" + suffixe, "Cuisine", suffixe, "Cuisine", LocalDate.now()
        ));
        Utilisateur utilisateur = utilisateurRepository.saveAndFlush(new Utilisateur(
                "cuisine-" + suffixe + "@example.com", "$2a$12$hash.detestuniquement", employe
        ));
        TableRestaurant table = tableRepository.saveAndFlush(
                new TableRestaurant(Math.abs(suffixe.hashCode()), 4)
        );
        Categorie categorie = categorieRepository.saveAndFlush(new Categorie("Cat-" + suffixe, null));
        Produit produit = produitRepository.saveAndFlush(
                new Produit("Produit-" + suffixe, null, new BigDecimal("12.50"), categorie)
        );
        Commande commande = new Commande("CMD-" + suffixe.toUpperCase(), table, null, utilisateur);
        commande.ajouterLigne(produit, 2, produit.getPrix(), remarque);
        commande.confirmer();
        return commandeRepository.saveAndFlush(commande);
    }

    private RestaurantUserPrincipal principalCuisine() {
        return principal("CUISINE_COMMANDES_GERER", "ROLE_CUISINE");
    }

    private RestaurantUserPrincipal principal(String... autorites) {
        return new RestaurantUserPrincipal(
                -1L, "test@example.com", null, true,
                List.of(autorites).stream().map(SimpleGrantedAuthority::new).toList()
        );
    }
}
