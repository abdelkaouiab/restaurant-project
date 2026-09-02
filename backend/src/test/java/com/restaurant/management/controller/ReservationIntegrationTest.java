package com.restaurant.management.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReservationIntegrationTest {
    private static final AtomicInteger TABLES = new AtomicInteger(30000);
    @Container static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("restaurant_reservations_integration")
            .withUsername("restaurant_test").withPassword(UUID.randomUUID().toString());
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRESQL::getUsername);
        r.add("spring.datasource.password", POSTGRESQL::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("springdoc.api-docs.enabled", () -> "false");
        r.add("springdoc.swagger-ui.enabled", () -> "false");
    }

    @Autowired MockMvc mockMvc; @Autowired ObjectMapper objectMapper;
    @Autowired EmployeRepository employeRepository; @Autowired UtilisateurRepository utilisateurRepository;
    @Autowired TableRestaurantRepository tableRepository; @Autowired ClientRepository clientRepository;
    @Autowired ReservationRepository reservationRepository; @Autowired CommandeRepository commandeRepository;
    @Autowired RoleRepository roleRepository;

    @Test void permissionEstAttribueeAuxRolesPrevus() {
        assertThat(roleRepository.findWithPermissionsByNom(NomRole.SERVEUR).orElseThrow().getPermissions())
                .extracting(p -> p.getNom()).contains(NomPermission.RESERVATION_GERER);
        assertThat(roleRepository.findWithPermissionsByNom(NomRole.CAISSIER).orElseThrow().getPermissions())
                .extracting(p -> p.getNom()).doesNotContain(NomPermission.RESERVATION_GERER);
    }

    @Test void autorisationsSontControlees() throws Exception {
        mockMvc.perform(get("/api/reservations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reservations").with(user(principal(-1L, "CLIENT_CONSULTER"))))
                .andExpect(status().isForbidden());
    }

    @Test void listeEstFiltreeParDateEtStatutAvantPagination() throws Exception {
        Contexte c = contexte(4);
        LocalDate date = LocalDate.now(ZoneId.of("Africa/Casablanca")).plusDays(30);
        Instant debutCible = date.atTime(18, 0).atZone(ZoneId.of("Africa/Casablanca")).toInstant();
        long cible = id(creer(c, debutCible, 2).andExpect(status().isCreated()).andReturn());
        transition(c, cible, "confirmation").andExpect(status().isOk());

        long autreStatut = id(creer(c, debutCible.plus(Duration.ofHours(3)), 2)
                .andExpect(status().isCreated()).andReturn());
        long autreDate = id(creer(c, debutCible.plus(Duration.ofDays(1)), 2)
                .andExpect(status().isCreated()).andReturn());
        transition(c, autreDate, "confirmation").andExpect(status().isOk());

        mockMvc.perform(get("/api/reservations")
                        .param("date", date.toString())
                        .param("statut", "CONFIRMEE")
                        .param("page", "0")
                        .param("taille", "10")
                        .with(user(c.principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu[0].id").value(cible))
                .andExpect(jsonPath("$.contenu[?(@.id == %d)]".formatted(autreStatut)).doesNotExist())
                .andExpect(jsonPath("$.contenu[?(@.id == %d)]".formatted(autreDate)).doesNotExist());

        mockMvc.perform(get("/api/reservations")
                        .param("statut", "INCONNU")
                        .with(user(c.principal())))
                .andExpect(status().isBadRequest());
    }

    @Test void capaciteEtConflitDeCreneauSontRefuses() throws Exception {
        Contexte c = contexte(4); Instant debut = Instant.now().plus(Duration.ofDays(2));
        creer(c, debut, 5).andExpect(status().isConflict());
        creer(c, debut, 4).andExpect(status().isCreated())
                .andExpect(jsonPath("$.fin").isNotEmpty());
        creer(c, debut.plus(Duration.ofHours(1)), 2).andExpect(status().isConflict());
        creer(c, debut.plus(Duration.ofHours(2)), 2).andExpect(status().isCreated());
    }

    @Test void transitionsEtLiberationDeTableSontControlees() throws Exception {
        Contexte c = contexte(4); Instant debut = Instant.now().plus(Duration.ofDays(3));
        long id = id(creer(c, debut, 2).andExpect(status().isCreated()).andReturn());
        transition(c, id, "arrivee").andExpect(status().isConflict());
        transition(c, id, "confirmation").andExpect(status().isOk());
        transition(c, id, "arrivee").andExpect(status().isOk());
        transition(c, id, "terminaison").andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("TERMINEE"));
        creer(c, debut.plus(Duration.ofMinutes(30)), 2).andExpect(status().isCreated());

        long absent = id(creer(c, debut.plus(Duration.ofDays(1)), 2).andReturn());
        transition(c, absent, "confirmation").andExpect(status().isOk());
        transition(c, absent, "absence").andExpect(status().isOk());
        transition(c, absent, "arrivee").andExpect(status().isConflict());
    }

    @Test void commandeEstCreeeUneSeuleFoisApresArrivee() throws Exception {
        Contexte c = contexte(4);
        long id = id(creer(c, Instant.now().plus(Duration.ofDays(4)), 2).andReturn());
        creerCommande(c, id).andExpect(status().isConflict());
        transition(c, id, "confirmation").andExpect(status().isOk());
        transition(c, id, "arrivee").andExpect(status().isOk());
        creerCommande(c, id).andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroCommande").isNotEmpty());
        creerCommande(c, id).andExpect(status().isConflict());
        Commande commande = commandeRepository.findByReservationId(id).orElseThrow();
        assertThat(commande.getStatut()).isEqualTo(StatutCommande.BROUILLON);
        assertThat(commande.getClient().getId()).isEqualTo(c.client().getId());
        assertThat(commande.getTable().getId()).isEqualTo(c.table().getId());
    }

    @Test void demandesConcurrentesPourLaMemeTableSontSerialisees() throws Exception {
        Contexte c = contexte(4); Instant debut = Instant.now().plus(Duration.ofDays(5));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Integer> a = executor.submit(() -> creer(c, debut, 2).andReturn().getResponse().getStatus());
        Future<Integer> b = executor.submit(() -> creer(c, debut.plusSeconds(60), 2).andReturn().getResponse().getStatus());
        assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(201, 409);
        executor.shutdownNow();
    }

    private ResultActions creer(Contexte c, Instant debut, int personnes) throws Exception {
        return mockMvc.perform(post("/api/reservations").with(user(c.principal())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tableId":%d,"clientId":%d,"nombrePersonnes":%d,"debut":"%s","remarques":"Fenêtre"}
                    """.formatted(c.table().getId(), c.client().getId(), personnes, debut)));
    }
    private ResultActions transition(Contexte c, long id, String action) throws Exception {
        return mockMvc.perform(post("/api/reservations/{id}/{action}", id, action)
                .with(user(c.principal())).with(csrf()));
    }
    private ResultActions creerCommande(Contexte c, long id) throws Exception {
        return mockMvc.perform(post("/api/reservations/{id}/commande", id)
                .with(user(c.principal())).with(csrf()));
    }
    private long id(MvcResult r) throws Exception { return objectMapper.readTree(r.getResponse().getContentAsByteArray()).path("id").asLong(); }
    private Contexte contexte(int capacite) {
        String s = UUID.randomUUID().toString().substring(0, 8);
        Employe e = employeRepository.saveAndFlush(new Employe("RES-" + s, "Serveur", s, "Salle", LocalDate.now()));
        Utilisateur u = utilisateurRepository.saveAndFlush(new Utilisateur("res-" + s + "@example.com", "$2a$12$test", e));
        TableRestaurant t = tableRepository.saveAndFlush(new TableRestaurant(TABLES.incrementAndGet(), capacite));
        Client client = clientRepository.saveAndFlush(new Client("Client", s, null, null));
        return new Contexte(t, client, principal(u.getId(), "RESERVATION_GERER", "COMMANDE_GERER", "ROLE_SERVEUR"));
    }
    private RestaurantUserPrincipal principal(Long id, String... auth) {
        return new RestaurantUserPrincipal(id, "p-" + id + "@example.com", null, true,
                Arrays.stream(auth).map(SimpleGrantedAuthority::new).toList());
    }
    private record Contexte(TableRestaurant table, Client client, RestaurantUserPrincipal principal) {}
}
