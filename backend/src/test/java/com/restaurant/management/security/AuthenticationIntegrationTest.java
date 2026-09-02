package com.restaurant.management.security;

import com.restaurant.management.entity.Employe;
import com.restaurant.management.entity.JournalActivite;
import com.restaurant.management.entity.Role;
import com.restaurant.management.entity.Utilisateur;
import com.restaurant.management.enums.NomRole;
import com.restaurant.management.enums.ResultatActivite;
import com.restaurant.management.repository.EmployeRepository;
import com.restaurant.management.repository.JournalActiviteRepository;
import com.restaurant.management.repository.RoleRepository;
import com.restaurant.management.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthenticationIntegrationTest {

    private static final String TEST_PASSWORD = "Test-only-password-42!";
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("restaurant_auth_integration")
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
        registry.add("server.servlet.session.cookie.secure", () -> "true");
        registry.add("app.security.cors.allowed-origins", () -> ALLOWED_ORIGIN);
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
    private JournalActiviteRepository journalActiviteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void successfulLoginCreatesSessionAndReturnsSafeCurrentUser() throws Exception {
        Utilisateur utilisateur = createUser(true);

        MvcResult loginResult = login(utilisateur.getEmail(), TEST_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.email").value(utilisateur.getEmail()))
                .andExpect(jsonPath("$.roles[0]").value(NomRole.GERANT.name()))
                .andExpect(jsonPath("$.motDePasse").doesNotExist())
                .andExpect(jsonPath("$.motDePasseHash").doesNotExist())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(utilisateur.getId()))
                .andExpect(jsonPath("$.employe.matricule").value(utilisateur.getEmploye().getMatricule()))
                .andExpect(jsonPath("$.motDePasse").doesNotExist())
                .andExpect(jsonPath("$.motDePasseHash").doesNotExist());

        List<JournalActivite> activities = journalActiviteRepository.findAll();
        assertThat(activities)
                .anySatisfy(activity -> {
                    assertThat(activity.getAction()).isEqualTo("CONNEXION");
                    assertThat(activity.getResultat()).isEqualTo(ResultatActivite.SUCCES);
                    assertThat(activity.getUtilisateur().getId()).isEqualTo(utilisateur.getId());
                    assertThat(activity.getDetails()).isNull();
                });
    }

    @Test
    void wrongPasswordReturnsGenericErrorAndSafeAudit() throws Exception {
        Utilisateur utilisateur = createUser(true);

        login(utilisateur.getEmail(), "Incorrect-test-value")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value("E-mail ou mot de passe incorrect."))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.motDePasse").doesNotExist());

        assertThat(journalActiviteRepository.findAll())
                .anySatisfy(activity -> {
                    assertThat(activity.getAction()).isEqualTo("CONNEXION");
                    assertThat(activity.getResultat()).isEqualTo(ResultatActivite.ECHEC);
                    assertThat(activity.getUtilisateur()).isNull();
                    assertThat(activity.getDetails()).isNull();
                });
    }

    @Test
    void unknownUserReturnsSameGenericError() throws Exception {
        login("unknown-" + UUID.randomUUID() + "@example.com", TEST_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value("E-mail ou mot de passe incorrect."));
    }

    @Test
    void inactiveUserReturnsSameGenericError() throws Exception {
        Utilisateur utilisateur = createUser(false);

        login(utilisateur.getEmail(), TEST_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value("E-mail ou mot de passe incorrect."));
    }

    @Test
    void logoutInvalidatesSessionAndIsAudited() throws Exception {
        Utilisateur utilisateur = createUser(true);
        MvcResult loginResult = login(utilisateur.getEmail(), TEST_PASSWORD)
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(journalActiviteRepository.findAll())
                .anySatisfy(activity -> {
                    assertThat(activity.getAction()).isEqualTo("DECONNEXION");
                    assertThat(activity.getResultat()).isEqualTo(ResultatActivite.SUCCES);
                });
    }

    @Test
    void unauthenticatedAccessIsRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentification requise."));
    }

    @Test
    void loginWithoutCsrfIsRejected() throws Exception {
        Utilisateur utilisateur = createUser(true);
        String body = loginBody(utilisateur.getEmail(), TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void csrfEndpointReturnsTokenWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void corsAcceptsConfiguredOriginAndApiMethods() throws Exception {
        for (String method : List.of("GET", "POST", "PUT", "PATCH", "DELETE")) {
            mockMvc.perform(options("/api/admin/utilisateurs/1/roles")
                            .header("Origin", ALLOWED_ORIGIN)
                            .header("Access-Control-Request-Method", method))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }

        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Invalid CORS request"));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password)));
    }

    private String loginBody(String email, String password) {
        return """
                {"email":"%s","motDePasse":"%s"}
                """.formatted(email, password);
    }

    private Utilisateur createUser(boolean active) {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        Employe employe = employeRepository.saveAndFlush(new Employe(
                "AUTH-" + suffix,
                "Test",
                "Authentification",
                "Gérant",
                LocalDate.now()
        ));
        Role role = roleRepository.findByNom(NomRole.GERANT).orElseThrow();
        Utilisateur utilisateur = new Utilisateur(
                "auth-" + suffix + "@example.com",
                passwordEncoder.encode(TEST_PASSWORD),
                employe
        );
        utilisateur.addRole(role);
        utilisateur.setActif(active);
        return utilisateurRepository.saveAndFlush(utilisateur);
    }
}
