package com.restaurant.management.controller;

import com.restaurant.management.entity.Categorie;
import com.restaurant.management.entity.Client;
import com.restaurant.management.entity.Commande;
import com.restaurant.management.entity.Employe;
import com.restaurant.management.entity.JournalActivite;
import com.restaurant.management.entity.Permission;
import com.restaurant.management.entity.Produit;
import com.restaurant.management.entity.Role;
import com.restaurant.management.entity.Reservation;
import com.restaurant.management.entity.TableRestaurant;
import com.restaurant.management.entity.Utilisateur;
import com.restaurant.management.enums.NomPermission;
import com.restaurant.management.enums.NomRole;
import com.restaurant.management.repository.CategorieRepository;
import com.restaurant.management.repository.ClientRepository;
import com.restaurant.management.repository.CommandeRepository;
import com.restaurant.management.repository.EmployeRepository;
import com.restaurant.management.repository.JournalActiviteRepository;
import com.restaurant.management.repository.ProduitRepository;
import com.restaurant.management.repository.ReservationRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
class ReferentielsRestaurantIntegrationTest {

    private static final Path REPERTOIRE_IMAGES = Path.of(
            System.getProperty("java.io.tmpdir"), "restaurant-product-images-" + UUID.randomUUID()
    );

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("restaurant_referentiels_integration")
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
        registry.add("app.product-images.directory", REPERTOIRE_IMAGES::toString);
    }

    @Autowired MockMvc mockMvc;
    @Autowired CategorieRepository categorieRepository;
    @Autowired ProduitRepository produitRepository;
    @Autowired TableRestaurantRepository tableRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired CommandeRepository commandeRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired EmployeRepository employeRepository;
    @Autowired UtilisateurRepository utilisateurRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired JournalActiviteRepository journalRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void accesNonAuthentifieEtRoleInsuffisantSontRefuses() throws Exception {
        mockMvc.perform(get("/api/produits")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/produits").with(user(principal("EMPLOYE_CONSULTER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void migrationsAttribuentLesPermissionsSelonLesRoles() {
        Role administrateur = roleRepository.findWithPermissionsByNom(NomRole.ADMINISTRATEUR).orElseThrow();
        Role serveur = roleRepository.findWithPermissionsByNom(NomRole.SERVEUR).orElseThrow();
        Set<NomPermission> permissionsAdmin = administrateur.getPermissions().stream()
                .map(Permission::getNom).collect(Collectors.toSet());
        Set<NomPermission> permissionsServeur = serveur.getPermissions().stream()
                .map(Permission::getNom).collect(Collectors.toSet());

        assertThat(permissionsAdmin).contains(
                NomPermission.CLIENT_GERER, NomPermission.TABLE_GERER,
                NomPermission.CATEGORIE_GERER, NomPermission.PRODUIT_GERER
        );
        assertThat(permissionsServeur)
                .contains(NomPermission.CLIENT_GERER, NomPermission.TABLE_GERER, NomPermission.PRODUIT_CONSULTER)
                .doesNotContain(NomPermission.PRODUIT_GERER);
    }

    @Test
    void creationEtRechercheDesCinqReferentielsFonctionnent() throws Exception {
        long employeId = creerEmployeParApi();
        mockMvc.perform(get("/api/employes").param("recherche", "EMP-")
                        .with(user(principal("EMPLOYE_CONSULTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(post("/api/clients")
                        .with(user(principal("CLIENT_GERER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Client","prenom":"Test","email":"client@example.com",
                                 "telephone":"+212 600000000"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nom").value("Client"));

        mockMvc.perform(post("/api/tables")
                        .with(user(principal("TABLE_GERER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":12,\"capacite\":4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(12));

        Categorie categorie = categorieRepository.saveAndFlush(new Categorie("Boissons", "Boissons froides"));
        mockMvc.perform(post("/api/produits")
                        .with(user(principal("PRODUIT_GERER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Eau","description":"Bouteille","prix":12.50,"categorieId":%d}
                                """.formatted(categorie.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prix").value(12.50))
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.imageUrl").doesNotExist())
                .andExpect(jsonPath("$.categorie.id").value(categorie.getId()));

        assertThat(employeId).isPositive();
    }

    @Test
    void disponibiliteMetierEstExposeeFiltrableEtModifiable() throws Exception {
        Categorie categorie = categorieRepository.saveAndFlush(new Categorie("Disponibilite-" + suffixe(), null));

        mockMvc.perform(post("/api/produits")
                        .with(user(principal("PRODUIT_GERER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Produit indisponible","prix":15.00,"categorieId":%d,"disponible":false}
                                """.formatted(categorie.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disponible").value(false));

        mockMvc.perform(get("/api/produits")
                        .param("disponible", "false")
                        .with(user(principal("PRODUIT_CONSULTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu[?(@.nom == 'Produit indisponible' && @.disponible == false)]").isNotEmpty());
    }

    @Test
    void imageProduitValideePeutEtreAjouteeConsulteeEtRetiree() throws Exception {
        Categorie categorie = categorieRepository.saveAndFlush(new Categorie("Image-" + suffixe(), null));
        Produit produit = produitRepository.saveAndFlush(
                new Produit("Produit image", null, new BigDecimal("25.00"), categorie)
        );
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        );
        MockMultipartFile fichier = new MockMultipartFile("fichier", "produit.png", "image/png", png);

        mockMvc.perform(multipart("/api/produits/{id}/image", produit.getId())
                        .file(fichier)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .with(user(principal("PRODUIT_GERER"))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("/api/produits/" + produit.getId() + "/image"))
                .andExpect(jsonPath("$.imageTypeMime").value("image/png"));

        mockMvc.perform(get("/api/produits/{id}/image", produit.getId())
                        .with(user(principal("PRODUIT_CONSULTER"))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("image/png"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(png));

        mockMvc.perform(delete("/api/produits/{id}/image", produit.getId())
                        .with(user(principal("PRODUIT_GERER"))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
    }

    @Test
    void fausseImageEtPermissionInsuffisanteSontRefusees() throws Exception {
        Categorie categorie = categorieRepository.saveAndFlush(new Categorie("Image-invalide-" + suffixe(), null));
        Produit produit = produitRepository.saveAndFlush(
                new Produit("Produit image invalide", null, new BigDecimal("25.00"), categorie)
        );
        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "fausse.png", "image/png", "ceci n'est pas une image".getBytes()
        );

        mockMvc.perform(multipart("/api/produits/{id}/image", produit.getId())
                        .file(fichier)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .with(user(principal("PRODUIT_GERER"))).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));

        mockMvc.perform(delete("/api/produits/{id}/image", produit.getId())
                        .with(user(principal("PRODUIT_CONSULTER"))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void tablesExposentLeurSituationOperationnelleComplete() throws Exception {
        TableRestaurant libre = tableRepository.save(new TableRestaurant(21, 2));
        TableRestaurant reservee = tableRepository.save(new TableRestaurant(22, 4));
        TableRestaurant occupee = tableRepository.save(new TableRestaurant(23, 6));
        TableRestaurant horsService = new TableRestaurant(24, 8);
        horsService.setActif(false);
        tableRepository.saveAndFlush(horsService);

        Employe employe = employeRepository.saveAndFlush(new Employe(
                "SITUATION-" + suffixe(), "Auteur", "Test", "Serveur", LocalDate.now()
        ));
        Utilisateur auteur = new Utilisateur(
                "situation-" + suffixe() + "@example.com",
                passwordEncoder.encode("Test-only-password-42!"),
                employe
        );
        auteur.addRole(roleRepository.findByNom(NomRole.SERVEUR).orElseThrow());
        utilisateurRepository.saveAndFlush(auteur);

        Client client = clientRepository.saveAndFlush(new Client("Réservation", "Test", null, null));
        reservationRepository.saveAndFlush(new Reservation(
                "RES-SITUATION-" + suffixe(), reservee, client, auteur, 3,
                Instant.now().minus(Duration.ofMinutes(15)), null
        ));
        commandeRepository.saveAndFlush(new Commande(
                "CMD-SITUATION-" + suffixe(), occupee, null, auteur
        ));

        mockMvc.perform(get("/api/tables")
                        .param("recherche", "21")
                        .with(user(principal("TABLE_CONSULTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu[0].statutOperationnel").value("LIBRE"))
                .andExpect(jsonPath("$.contenu[0].disponible").value(true));

        mockMvc.perform(get("/api/tables").param("taille", "100")
                        .with(user(principal("TABLE_CONSULTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu[0].numero").value(libre.getNumero()))
                .andExpect(jsonPath("$.contenu[1].statutOperationnel").value("RESERVEE"))
                .andExpect(jsonPath("$.contenu[1].reservationEnCours.nombrePersonnes").value(3))
                .andExpect(jsonPath("$.contenu[2].statutOperationnel").value("OCCUPEE"))
                .andExpect(jsonPath("$.contenu[2].commandeAssociee.numero").isNotEmpty())
                .andExpect(jsonPath("$.contenu[3].statutOperationnel").value("HORS_SERVICE"))
                .andExpect(jsonPath("$.contenu[3].disponible").value(false));
    }

    @Test
    void validationsPrixCapaciteCategorieEtNumeroUniqueSontAppliquees() throws Exception {
        mockMvc.perform(post("/api/tables")
                        .with(user(principal("TABLE_GERER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":1,\"capacite\":0}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/produits")
                        .with(user(principal("PRODUIT_GERER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Invalide\",\"prix\":0}"))
                .andExpect(status().isBadRequest());

        creerTable(7);
        mockMvc.perform(post("/api/tables")
                        .with(user(principal("TABLE_GERER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":7,\"capacite\":6}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"));
    }

    @Test
    void triNonAutoriseEstRefuseEtPaginationEstBornee() throws Exception {
        mockMvc.perform(get("/api/clients").param("tri", "telephone.secret")
                        .with(user(principal("CLIENT_CONSULTER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/clients").param("taille", "101")
                        .with(user(principal("CLIENT_CONSULTER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void modificationPrixEtDesactivationSontJournalisees() throws Exception {
        Categorie categorie = categorieRepository.saveAndFlush(new Categorie("Plats", null));
        Produit produit = produitRepository.saveAndFlush(
                new Produit("Tajine", null, new BigDecimal("65.00"), categorie)
        );

        mockMvc.perform(put("/api/produits/{id}", produit.getId())
                        .with(user(principal("PRODUIT_GERER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Tajine","prix":70.00,"categorieId":%d}
                                """.formatted(categorie.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prix").value(70.00));

        mockMvc.perform(patch("/api/produits/{id}/statut", produit.getId())
                        .with(user(principal("PRODUIT_GERER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actif\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(false));

        assertThat(journalRepository.findAll()).extracting(JournalActivite::getAction)
                .contains("PRODUIT_PRIX_MODIFICATION", "PRODUIT_DESACTIVATION");
    }

    @Test
    void categorieAvecProduitActifNePeutPasEtreDesactivee() throws Exception {
        Categorie categorie = categorieRepository.saveAndFlush(new Categorie("Desserts", null));
        produitRepository.saveAndFlush(new Produit("Flan", null, new BigDecimal("20.00"), categorie));

        mockMvc.perform(patch("/api/categories/{id}/statut", categorie.getId())
                        .with(user(principal("CATEGORIE_GERER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actif\":false}"))
                .andExpect(status().isConflict());
    }

    @Test
    void employeDuDernierAdministrateurNePeutPasEtreDesactive() throws Exception {
        Employe employe = employeRepository.saveAndFlush(new Employe(
                "ADMIN-" + suffixe(), "Admin", "Unique", "Administration", LocalDate.now()
        ));
        Utilisateur utilisateur = new Utilisateur(
                "admin-" + suffixe() + "@example.com",
                passwordEncoder.encode("Test-only-password-42!"),
                employe
        );
        utilisateur.addRole(roleRepository.findByNom(NomRole.ADMINISTRATEUR).orElseThrow());
        utilisateurRepository.saveAndFlush(utilisateur);

        mockMvc.perform(patch("/api/employes/{id}/statut", employe.getId())
                        .with(user(principal("EMPLOYE_GERER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actif\":false}"))
                .andExpect(status().isConflict());
        assertThat(employeRepository.findById(employe.getId()).orElseThrow().isActif()).isTrue();
    }

    private long creerEmployeParApi() throws Exception {
        String matricule = "EMP-" + suffixe();
        String contenu = """
                {"matricule":"%s","nom":"Employe","prenom":"Test","telephone":"+212 600000001",
                 "poste":"Serveur","dateEmbauche":"2026-01-01"}
                """.formatted(matricule);
        String location = mockMvc.perform(post("/api/employes")
                        .with(user(principal("EMPLOYE_GERER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(contenu))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.matricule").value(matricule))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(location.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private void creerTable(int numero) throws Exception {
        mockMvc.perform(post("/api/tables")
                        .with(user(principal("TABLE_GERER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":" + numero + ",\"capacite\":4}"))
                .andExpect(status().isCreated());
    }

    private RestaurantUserPrincipal principal(String... permissions) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(permissions)
                .map(SimpleGrantedAuthority::new).toList();
        return new RestaurantUserPrincipal(-1L, "test@example.com", null, true, authorities);
    }

    private String suffixe() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
