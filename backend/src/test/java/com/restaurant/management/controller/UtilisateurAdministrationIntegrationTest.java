package com.restaurant.management.controller;

import com.restaurant.management.entity.Employe;
import com.restaurant.management.entity.Role;
import com.restaurant.management.entity.Utilisateur;
import com.restaurant.management.enums.NomRole;
import com.restaurant.management.repository.EmployeRepository;
import com.restaurant.management.repository.JournalActiviteRepository;
import com.restaurant.management.repository.RoleRepository;
import com.restaurant.management.repository.UtilisateurRepository;
import com.restaurant.management.security.RestaurantUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class UtilisateurAdministrationIntegrationTest {

    private static final String TEST_PASSWORD = "Test-only-password-42!";

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("restaurant_user_admin_integration")
                    .withUsername("restaurant_test")
                    .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void configurePostgresql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("springdoc.api-docs.enabled", () -> "false");
        registry.add("springdoc.swagger-ui.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeRepository employeRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JournalActiviteRepository journalRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void accesNonAuthentifieRetourne401() throws Exception {
        mockMvc.perform(get("/api/admin/utilisateurs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void accesSansPermissionRetourne403() throws Exception {
        mockMvc.perform(get("/api/admin/utilisateurs")
                        .with(user(principal("EMPLOYE_CONSULTER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void administrateurPeutCreerEtRechercherSansExposerLeMotDePasse() throws Exception {
        Employe employe = creerEmploye("CREATE");
        String email = "admin-created-" + suffixe() + "@example.com";
        String json = """
                {
                  "email": "%s",
                  "motDePasse": "%s",
                  "employeId": %d,
                  "roles": ["GERANT"]
                }
                """.formatted(email, TEST_PASSWORD, employe.getId());

        MvcResult resultat = mockMvc.perform(post("/api/admin/utilisateurs")
                        .with(user(principal("UTILISATEUR_GERER", "ROLE_GERER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("GERANT"))
                .andExpect(jsonPath("$.motDePasse").doesNotExist())
                .andExpect(jsonPath("$.motDePasseHash").doesNotExist())
                .andReturn();

        assertThat(resultat.getResponse().getContentAsString())
                .doesNotContain(TEST_PASSWORD)
                .doesNotContain("$2a$")
                .doesNotContain("$2b$");
        Utilisateur sauvegarde = utilisateurRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(passwordEncoder.matches(TEST_PASSWORD, sauvegarde.getMotDePasseHash())).isTrue();
        assertThat(journalRepository.findAll())
                .anySatisfy(entree -> assertThat(entree.getAction()).isEqualTo("UTILISATEUR_CREATION"));

        mockMvc.perform(get("/api/admin/utilisateurs")
                        .param("recherche", email)
                        .with(user(principal("UTILISATEUR_CONSULTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu[0].email").value(email))
                .andExpect(jsonPath("$.contenu[0].motDePasseHash").doesNotExist());
    }

    @Test
    void rechercheSansFiltreRetourneLesUtilisateursExistants() throws Exception {
        Utilisateur utilisateur = creerUtilisateur(NomRole.GERANT, true);

        mockMvc.perform(get("/api/admin/utilisateurs")
                        .param("tri", "email")
                        .param("direction", "asc")
                        .with(user(principal("UTILISATEUR_CONSULTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu[0].id").value(utilisateur.getId()))
                .andExpect(jsonPath("$.contenu[0].motDePasseHash").doesNotExist());
    }

    @Test
    void modificationDesRolesSansPermissionRoleGererEstInterdite() throws Exception {
        Utilisateur utilisateur = creerUtilisateur(NomRole.GERANT, true);

        mockMvc.perform(put("/api/admin/utilisateurs/{id}/roles", utilisateur.getId())
                        .with(user(principal("UTILISATEUR_GERER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"SERVEUR\"]}"))
                .andExpect(status().isForbidden());

        Utilisateur recharge = utilisateurRepository.findWithEmployeAndRolesById(utilisateur.getId()).orElseThrow();
        assertThat(recharge.getRoles()).extracting(Role::getNom).containsExactly(NomRole.GERANT);
    }

    @Test
    void dernierAdministrateurActifNePeutPasEtreDesactive() throws Exception {
        Utilisateur administrateur = creerUtilisateur(NomRole.ADMINISTRATEUR, true);

        mockMvc.perform(patch("/api/admin/utilisateurs/{id}/statut", administrateur.getId())
                        .with(user(principal("UTILISATEUR_GERER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actif\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"));

        assertThat(utilisateurRepository.findById(administrateur.getId()).orElseThrow().isActif()).isTrue();
    }

    @Test
    void dernierAdministrateurActifNePeutPasPerdreSonRole() throws Exception {
        Utilisateur administrateur = creerUtilisateur(NomRole.ADMINISTRATEUR, true);

        mockMvc.perform(put("/api/admin/utilisateurs/{id}/roles", administrateur.getId())
                        .with(user(principal("ROLE_GERER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"GERANT\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"));

        assertThat(utilisateurRepository.findWithEmployeAndRolesById(administrateur.getId()).orElseThrow().getRoles())
                .extracting(Role::getNom)
                .contains(NomRole.ADMINISTRATEUR);
    }

    @Test
    void administrateurPeutModifierRolesStatutEtMotDePasse() throws Exception {
        Utilisateur utilisateur = creerUtilisateur(NomRole.GERANT, true);
        String nouvelEmail = "updated-" + suffixe() + "@example.com";
        String nouveauMotDePasse = "New-test-password-84!";

        mockMvc.perform(put("/api/admin/utilisateurs/{id}", utilisateur.getId())
                        .with(user(principal("UTILISATEUR_GERER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + nouvelEmail + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(nouvelEmail));

        mockMvc.perform(put("/api/admin/utilisateurs/{id}/roles", utilisateur.getId())
                        .with(user(principal("ROLE_GERER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"SERVEUR\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("SERVEUR"));

        mockMvc.perform(patch("/api/admin/utilisateurs/{id}/statut", utilisateur.getId())
                        .with(user(principal("UTILISATEUR_GERER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actif\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(false));

        mockMvc.perform(post("/api/admin/utilisateurs/{id}/mot-de-passe/reinitialisation", utilisateur.getId())
                        .with(user(principal("UTILISATEUR_GERER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nouveauMotDePasse\":\"" + nouveauMotDePasse + "\"}"))
                .andExpect(status().isNoContent());

        Utilisateur recharge = utilisateurRepository.findWithEmployeAndRolesById(utilisateur.getId()).orElseThrow();
        assertThat(recharge.getEmail()).isEqualTo(nouvelEmail);
        assertThat(recharge.isActif()).isFalse();
        assertThat(passwordEncoder.matches(nouveauMotDePasse, recharge.getMotDePasseHash())).isTrue();
    }

    @Test
    void champDeTriHorsListeBlancheEstRefuse() throws Exception {
        mockMvc.perform(get("/api/admin/utilisateurs")
                        .param("tri", "motDePasseHash")
                        .with(user(principal("UTILISATEUR_CONSULTER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private Employe creerEmploye(String prefixe) {
        String suffixe = suffixe();
        return employeRepository.saveAndFlush(new Employe(
                prefixe + "-" + suffixe,
                "Nom",
                "Prenom",
                "Test",
                LocalDate.now()
        ));
    }

    private Utilisateur creerUtilisateur(NomRole nomRole, boolean actif) {
        Employe employe = creerEmploye("USER");
        Utilisateur utilisateur = new Utilisateur(
                "user-" + suffixe() + "@example.com",
                passwordEncoder.encode(TEST_PASSWORD),
                employe
        );
        utilisateur.addRole(roleRepository.findByNom(nomRole).orElseThrow());
        utilisateur.setActif(actif);
        return utilisateurRepository.saveAndFlush(utilisateur);
    }

    private RestaurantUserPrincipal principal(String... permissions) {
        List<SimpleGrantedAuthority> authorities = List.of(permissions).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new RestaurantUserPrincipal(-1L, "test-admin@example.com", null, true, authorities);
    }

    private String suffixe() {
        return UUID.randomUUID().toString().substring(0, 10);
    }
}
