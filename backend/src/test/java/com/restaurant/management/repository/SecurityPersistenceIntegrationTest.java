package com.restaurant.management.repository;

import com.restaurant.management.entity.Employe;
import com.restaurant.management.entity.JournalActivite;
import com.restaurant.management.entity.Role;
import com.restaurant.management.entity.Utilisateur;
import com.restaurant.management.enums.NomPermission;
import com.restaurant.management.enums.NomRole;
import com.restaurant.management.enums.ResultatActivite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("restaurant_integration")
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
    private EmployeRepository employeRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private JournalActiviteRepository journalActiviteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Order(1)
    void flywayCreatesSchemaAndSeedsOnlyRolesAndPermissions() {
        assertThat(countRows("roles")).isEqualTo(NomRole.values().length);
        assertThat(countRows("permissions")).isEqualTo(NomPermission.values().length);
        assertThat(countRows("utilisateurs")).isZero();

        Role administrateur = roleRepository.findWithPermissionsByNom(NomRole.ADMINISTRATEUR).orElseThrow();
        assertThat(administrateur.getPermissions()).hasSize(NomPermission.values().length);
    }

    @Test
    @Order(2)
    @Transactional
    void repositoriesPersistEmployeeUserRolesAndActivityJournal() {
        Employe employe = employeRepository.save(new Employe(
                matricule(),
                "Alami",
                "Samira",
                "Gérante",
                LocalDate.of(2026, 7, 1)
        ));

        Role gerant = roleRepository.findByNom(NomRole.GERANT).orElseThrow();
        Utilisateur utilisateur = new Utilisateur(
                "Samira.Alami@Example.COM",
                "integration-test-hash-" + UUID.randomUUID(),
                employe
        );
        utilisateur.addRole(gerant);
        utilisateur = utilisateurRepository.saveAndFlush(utilisateur);

        UUID correlationId = UUID.randomUUID();
        JournalActivite activite = journalActiviteRepository.saveAndFlush(new JournalActivite(
                utilisateur,
                "CREATION_UTILISATEUR",
                "UTILISATEUR",
                utilisateur.getId().toString(),
                ResultatActivite.SUCCES,
                correlationId
        ));

        Utilisateur charge = utilisateurRepository
                .findWithEmployeAndRolesById(utilisateur.getId())
                .orElseThrow();

        assertThat(charge.getEmail()).isEqualTo("samira.alami@example.com");
        assertThat(charge.getEmploye().getMatricule()).isEqualTo(employe.getMatricule());
        assertThat(charge.getRoles()).extracting(Role::getNom).containsExactly(NomRole.GERANT);
        assertThat(activite.getCreeLe()).isNotNull();
        assertThat(journalActiviteRepository.findByCorrelationId(
                correlationId,
                org.springframework.data.domain.Pageable.unpaged()
        )).hasSize(1);
    }

    @Test
    @Order(3)
    void databaseRejectsDuplicateEmailIgnoringCase() {
        String email = "unique-" + UUID.randomUUID() + "@example.com";

        Employe premierEmploye = employeRepository.saveAndFlush(new Employe(
                matricule(), "Nom", "Premier", "Serveur", LocalDate.now()
        ));
        utilisateurRepository.saveAndFlush(new Utilisateur(
                email,
                "integration-test-hash-" + UUID.randomUUID(),
                premierEmploye
        ));

        Employe secondEmploye = employeRepository.saveAndFlush(new Employe(
                matricule(), "Nom", "Second", "Serveur", LocalDate.now()
        ));

        assertThatThrownBy(() -> utilisateurRepository.saveAndFlush(new Utilisateur(
                email.toUpperCase(),
                "integration-test-hash-" + UUID.randomUUID(),
                secondEmploye
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Order(4)
    void databaseRejectsBlankRequiredFieldsAndOversizedValues() {
        String insert = """
                INSERT INTO employes (matricule, nom, prenom, poste, date_embauche)
                VALUES (?, ?, ?, ?, ?)
                """;

        assertThatThrownBy(() -> jdbcTemplate.update(
                insert, matricule(), " ", "Prénom", "Serveur", LocalDate.now()
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                insert, "M".repeat(31), "Nom", "Prénom", "Serveur", LocalDate.now()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Order(5)
    @Transactional
    void mutableEntitiesUseOptimisticVersioning() {
        Employe employe = employeRepository.saveAndFlush(new Employe(
                matricule(), "Nom", "Version", "Caissier", LocalDate.now()
        ));
        long initialVersion = employe.getVersion();

        employe.setActif(false);
        employeRepository.saveAndFlush(employe);

        assertThat(employe.getVersion()).isEqualTo(initialVersion + 1);
        assertThat(employe.getModifieLe()).isAfterOrEqualTo(employe.getCreeLe());
    }

    private long countRows(String tableName) {
        Set<String> allowedTables = Set.of("roles", "permissions", "utilisateurs");
        if (!allowedTables.contains(tableName)) {
            throw new IllegalArgumentException("Table non autorisée");
        }
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return count == null ? 0 : count;
    }

    private static String matricule() {
        return "EMP-" + UUID.randomUUID().toString().substring(0, 12);
    }
}
