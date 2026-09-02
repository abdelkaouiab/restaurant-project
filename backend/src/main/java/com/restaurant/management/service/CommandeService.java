package com.restaurant.management.service;

import com.restaurant.management.dto.AjouterLigneCommandeRequest;
import com.restaurant.management.dto.CommandeResponse;
import com.restaurant.management.dto.CreerCommandeRequest;
import com.restaurant.management.dto.ModifierAssociationCommandeRequest;
import com.restaurant.management.dto.ModifierQuantiteLigneRequest;
import com.restaurant.management.dto.PageResponse;
import com.restaurant.management.entity.Client;
import com.restaurant.management.entity.Commande;
import com.restaurant.management.entity.Produit;
import com.restaurant.management.entity.TableRestaurant;
import com.restaurant.management.entity.Utilisateur;
import com.restaurant.management.enums.StatutCommande;
import com.restaurant.management.exception.BusinessConflictException;
import com.restaurant.management.exception.ResourceNotFoundException;
import com.restaurant.management.mapper.CommandeMapper;
import com.restaurant.management.repository.ClientRepository;
import com.restaurant.management.repository.CommandeRepository;
import com.restaurant.management.repository.ProduitRepository;
import com.restaurant.management.repository.TableRestaurantRepository;
import com.restaurant.management.repository.UtilisateurRepository;
import com.restaurant.management.security.RestaurantUserPrincipal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@Service
public class CommandeService {

    private static final Map<String, String> TRI = Map.of(
            "numero", "numero",
            "statut", "statut",
            "montantTotal", "montantTotal",
            "creeLe", "creeLe",
            "modifieLe", "modifieLe"
    );

    private final CommandeRepository repository;
    private final TableRestaurantRepository tableRepository;
    private final ClientRepository clientRepository;
    private final ProduitRepository produitRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CommandeMapper mapper;
    private final JournalModificationService journal;
    private final StockService stockService;

    public CommandeService(
            CommandeRepository repository,
            TableRestaurantRepository tableRepository,
            ClientRepository clientRepository,
            ProduitRepository produitRepository,
            UtilisateurRepository utilisateurRepository,
            CommandeMapper mapper,
            JournalModificationService journal,
            StockService stockService
    ) {
        this.repository = repository;
        this.tableRepository = tableRepository;
        this.clientRepository = clientRepository;
        this.produitRepository = produitRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.mapper = mapper;
        this.journal = journal;
        this.stockService = stockService;
    }

    @Transactional
    @PreAuthorize("hasAuthority('COMMANDE_GERER')")
    public CommandeResponse creer(CreerCommandeRequest request) {
        Utilisateur createur = utilisateurRepository.findById(principal().getUtilisateurId())
                .orElseThrow(ResourceNotFoundException::new);
        TableRestaurant table = trouverTableActive(request.tableId());
        Client client = trouverClientActif(request.clientId());
        Commande commande = repository.saveAndFlush(
                new Commande(genererNumero(), table, client, createur)
        );
        journal.journaliser("COMMANDE_CREATION", "COMMANDE", commande.getId());
        return mapper.versResponse(commande);
    }

    @Transactional
    @PreAuthorize("hasAuthority('COMMANDE_GERER')")
    public CommandeResponse modifierAssociation(Long id, ModifierAssociationCommandeRequest request) {
        Commande commande = trouverEtAutoriserModification(id);
        executerTransition(() -> commande.modifierAssociation(
                trouverTableActive(request.tableId()),
                trouverClientActif(request.clientId())
        ));
        repository.flush();
        return mapper.versResponse(commande);
    }

    @Transactional
    @PreAuthorize("hasAuthority('COMMANDE_GERER')")
    public CommandeResponse ajouterLigne(Long id, AjouterLigneCommandeRequest request) {
        Commande commande = trouverEtAutoriserModification(id);
        Produit produit = trouverProduitDisponible(request.produitId());
        boolean dejaPresent = commande.getLignes().stream()
                .anyMatch(ligne -> ligne.getProduit().getId().equals(produit.getId()));
        if (dejaPresent) {
            throw new BusinessConflictException("Ce produit est déjà présent dans la commande.");
        }
        executerTransition(() -> commande.ajouterLigne(
                produit, request.quantite(), produit.getPrix(), request.remarque()
        ));
        repository.flush();
        return mapper.versResponse(commande);
    }

    @Transactional
    @PreAuthorize("hasAuthority('COMMANDE_GERER')")
    public CommandeResponse modifierQuantite(
            Long id,
            Long ligneId,
            ModifierQuantiteLigneRequest request
    ) {
        Commande commande = trouverEtAutoriserModification(id);
        executerTransition(() -> commande.modifierLigne(ligneId, request.quantite(), request.remarque()));
        repository.flush();
        return mapper.versResponse(commande);
    }

    @Transactional
    @PreAuthorize("hasAuthority('COMMANDE_GERER')")
    public CommandeResponse supprimerLigne(Long id, Long ligneId) {
        Commande commande = trouverEtAutoriserModification(id);
        executerTransition(() -> commande.supprimerLigne(ligneId));
        repository.flush();
        return mapper.versResponse(commande);
    }

    @Transactional
    @PreAuthorize("hasAuthority('COMMANDE_GERER')")
    public CommandeResponse confirmer(Long id) {
        Commande commande = trouverEtAutoriserModification(id);
        verifierDisponibilitePourConfirmation(commande);
        executerTransition(commande::confirmer);
        stockService.deduireConfirmation(commande);
        repository.flush();
        journal.journaliser("COMMANDE_CONFIRMATION", "COMMANDE", id);
        return mapper.versResponse(commande);
    }

    @Transactional
    @PreAuthorize("hasAuthority('COMMANDE_GERER')")
    public CommandeResponse annuler(Long id) {
        Commande commande = trouverEtAutoriserModification(id);
        executerTransition(commande::annuler);
        repository.flush();
        journal.journaliser("COMMANDE_ANNULATION", "COMMANDE", id);
        return mapper.versResponse(commande);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('COMMANDE_CONSULTER')")
    public CommandeResponse obtenir(Long id) {
        Commande commande = trouver(id);
        verifierLectureAutorisee(commande);
        return mapper.versResponse(commande);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('COMMANDE_CONSULTER')")
    public PageResponse<CommandeResponse> rechercher(
            String recherche,
            StatutCommande statut,
            int page,
            int taille,
            String tri,
            String direction
    ) {
        RestaurantUserPrincipal principal = principal();
        boolean limiterAuCreateur = estServeurSansSupervision();
        String terme = RechercheServiceSupport.terme(recherche);
        Specification<Commande> specification = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (statut != null) predicates.add(cb.equal(root.get("statut"), statut));
            if (limiterAuCreateur) {
                predicates.add(cb.equal(root.get("creePar").get("id"), principal.getUtilisateurId()));
            }
            if (terme != null) {
                predicates.add(cb.like(cb.lower(root.get("numero")), "%" + terme + "%"));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<CommandeResponse> resultat = repository.findAll(
                specification,
                RechercheServiceSupport.pageable(page, taille, tri, direction, TRI)
        ).map(mapper::versResponse);
        return PageResponse.depuis(resultat);
    }

    private Commande trouverEtAutoriserModification(Long id) {
        Commande commande = trouver(id);
        if (estServeurSansSupervision()
                && !commande.getCreePar().getId().equals(principal().getUtilisateurId())) {
            throw new AccessDeniedException("Commande hors périmètre.");
        }
        return commande;
    }

    private void verifierLectureAutorisee(Commande commande) {
        if (estServeurSansSupervision()
                && !commande.getCreePar().getId().equals(principal().getUtilisateurId())) {
            throw new AccessDeniedException("Commande hors périmètre.");
        }
    }

    private boolean estServeurSansSupervision() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean serveur = aAutorite(authentication, "ROLE_SERVEUR");
        boolean supervision = aAutorite(authentication, "ROLE_ADMINISTRATEUR")
                || aAutorite(authentication, "ROLE_GERANT");
        return serveur && !supervision;
    }

    private boolean aAutorite(Authentication authentication, String autorite) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(autorite));
    }

    private void verifierDisponibilitePourConfirmation(Commande commande) {
        if (!commande.getTable().isActif()) {
            throw new BusinessConflictException("La table associée n'est plus disponible.");
        }
        if (commande.getClient() != null && !commande.getClient().isActif()) {
            throw new BusinessConflictException("Le client associé n'est plus actif.");
        }
        for (var ligne : commande.getLignes()) {
            Produit produit = ligne.getProduit();
            if (!produit.isActif() || !produit.isDisponible() || !produit.getCategorie().isActif()) {
                throw new BusinessConflictException("Un produit de la commande n'est plus disponible.");
            }
        }
    }

    private TableRestaurant trouverTableActive(Long id) {
        TableRestaurant table = tableRepository.findById(id).orElseThrow(ResourceNotFoundException::new);
        if (!table.isActif()) throw new BusinessConflictException("La table n'est pas disponible.");
        return table;
    }

    private Client trouverClientActif(Long id) {
        if (id == null) return null;
        Client client = clientRepository.findById(id).orElseThrow(ResourceNotFoundException::new);
        if (!client.isActif()) throw new BusinessConflictException("Le client n'est pas actif.");
        return client;
    }

    private Produit trouverProduitDisponible(Long id) {
        Produit produit = produitRepository.findById(id).orElseThrow(ResourceNotFoundException::new);
        if (!produit.isActif() || !produit.isDisponible() || !produit.getCategorie().isActif()) {
            throw new BusinessConflictException("Le produit n'est pas disponible.");
        }
        return produit;
    }

    private Commande trouver(Long id) {
        return repository.findAvecDetailsById(id).orElseThrow(ResourceNotFoundException::new);
    }

    private RestaurantUserPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof RestaurantUserPrincipal utilisateur)) {
            throw new AccessDeniedException("Authentification requise.");
        }
        return utilisateur;
    }

    private String genererNumero() {
        String numero;
        do {
            numero = "CMD-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        } while (repository.existsByNumero(numero));
        return numero;
    }

    private void executerTransition(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new BusinessConflictException(exception.getMessage());
        }
    }
}
