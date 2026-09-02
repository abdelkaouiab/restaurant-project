package com.restaurant.management.service;

import com.restaurant.management.dto.ModifierActifRequest;
import com.restaurant.management.dto.PageResponse;
import com.restaurant.management.dto.ProduitRequest;
import com.restaurant.management.dto.ProduitResponse;
import com.restaurant.management.entity.Categorie;
import com.restaurant.management.entity.Produit;
import com.restaurant.management.exception.BusinessConflictException;
import com.restaurant.management.exception.ResourceNotFoundException;
import com.restaurant.management.mapper.ReferentielMapper;
import com.restaurant.management.repository.CategorieRepository;
import com.restaurant.management.repository.ProduitRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;

@Service
public class ProduitService {

    private static final Map<String, String> TRI = Map.of(
            "nom", "nom", "prix", "prix", "categorie", "categorie.nom",
            "disponible", "disponible", "creeLe", "creeLe", "modifieLe", "modifieLe"
    );
    private final ProduitRepository repository;
    private final CategorieRepository categorieRepository;
    private final ReferentielMapper mapper;
    private final JournalModificationService journal;
    private final ProduitImageStorageService images;

    public ProduitService(
            ProduitRepository repository,
            CategorieRepository categorieRepository,
            ReferentielMapper mapper,
            JournalModificationService journal,
            ProduitImageStorageService images
    ) {
        this.repository = repository;
        this.categorieRepository = categorieRepository;
        this.mapper = mapper;
        this.journal = journal;
        this.images = images;
    }

    @Transactional
    @PreAuthorize("hasAuthority('PRODUIT_GERER')")
    public ProduitResponse creer(ProduitRequest request) {
        Categorie categorie = trouverCategorieActive(request.categorieId());
        Produit produit = repository.save(
                new Produit(request.nom(), request.description(), request.prix(), categorie)
        );
        if (Boolean.FALSE.equals(request.disponible())) {
            produit.modifier(request.nom(), request.description(), request.prix(), categorie, false);
        }
        return mapper.versResponse(produit);
    }

    @Transactional
    @PreAuthorize("hasAuthority('PRODUIT_GERER')")
    public ProduitResponse modifier(Long id, ProduitRequest request) {
        Produit produit = trouver(id);
        Categorie categorie = trouverCategorieActive(request.categorieId());
        BigDecimal ancienPrix = produit.getPrix();
        boolean disponible = request.disponible() == null ? produit.isDisponible() : request.disponible();
        produit.modifier(request.nom(), request.description(), request.prix(), categorie, disponible);
        if (ancienPrix.compareTo(request.prix()) != 0) {
            journal.journaliser("PRODUIT_PRIX_MODIFICATION", "PRODUIT", id);
        }
        return mapper.versResponse(produit);
    }

    @Transactional
    @PreAuthorize("hasAuthority('PRODUIT_GERER')")
    public ProduitResponse enregistrerImage(Long id, MultipartFile fichier) {
        Produit produit = trouver(id);
        String ancienneImage = produit.getImageNomFichier();
        ProduitImageStorageService.ImageStockee nouvelleImage = images.stocker(fichier);
        try {
            produit.definirImage(
                    nouvelleImage.nom(), nouvelleImage.typeMime(), nouvelleImage.taille()
            );
            repository.flush();
            journal.journaliser("PRODUIT_IMAGE_MODIFICATION", "PRODUIT", id);
            nettoyerImageApresTransaction(nouvelleImage.nom(), ancienneImage);
            return mapper.versResponse(produit);
        } catch (RuntimeException exception) {
            images.supprimer(nouvelleImage.nom());
            throw exception;
        }
    }

    @Transactional
    @PreAuthorize("hasAuthority('PRODUIT_GERER')")
    public ProduitResponse supprimerImage(Long id) {
        Produit produit = trouver(id);
        if (!produit.possedeImage()) throw new ResourceNotFoundException();
        String ancienneImage = produit.getImageNomFichier();
        produit.retirerImage();
        repository.flush();
        journal.journaliser("PRODUIT_IMAGE_SUPPRESSION", "PRODUIT", id);
        supprimerImageApresValidation(ancienneImage);
        return mapper.versResponse(produit);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PRODUIT_CONSULTER')")
    public ProduitImageStorageService.ImageLue obtenirImage(Long id) {
        return images.lire(trouver(id));
    }

    @Transactional
    @PreAuthorize("hasAuthority('PRODUIT_GERER')")
    public ProduitResponse modifierActif(Long id, ModifierActifRequest request) {
        Produit produit = trouver(id);
        produit.setActif(request.actif());
        if (!request.actif()) journal.journaliser("PRODUIT_DESACTIVATION", "PRODUIT", id);
        return mapper.versResponse(produit);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PRODUIT_CONSULTER')")
    public ProduitResponse obtenir(Long id) {
        return mapper.versResponse(trouver(id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PRODUIT_CONSULTER')")
    public PageResponse<ProduitResponse> rechercher(
            String recherche, Long categorieId, Boolean actif, Boolean disponible,
            int page, int taille, String tri, String direction
    ) {
        String terme = RechercheServiceSupport.terme(recherche);
        Specification<Produit> specification = (root, query, cb) -> {
            var p = new ArrayList<Predicate>();
            if (actif != null) p.add(cb.equal(root.get("actif"), actif));
            if (disponible != null) p.add(cb.equal(root.get("disponible"), disponible));
            if (categorieId != null) p.add(cb.equal(root.get("categorie").get("id"), categorieId));
            if (terme != null) {
                String motif = "%" + terme + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("nom")), motif),
                        cb.like(cb.lower(root.get("description")), motif),
                        cb.like(cb.lower(root.get("categorie").get("nom")), motif)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<ProduitResponse> resultat = repository.findAll(
                specification, RechercheServiceSupport.pageable(page, taille, tri, direction, TRI)
        ).map(mapper::versResponse);
        return PageResponse.depuis(resultat);
    }

    private Produit trouver(Long id) {
        return repository.findById(id).orElseThrow(ResourceNotFoundException::new);
    }

    private Categorie trouverCategorieActive(Long id) {
        Categorie categorie = categorieRepository.findById(id).orElseThrow(ResourceNotFoundException::new);
        if (!categorie.isActif()) {
            throw new BusinessConflictException("La catégorie du produit doit être active.");
        }
        return categorie;
    }

    private void nettoyerImageApresTransaction(String nouvelleImage, String ancienneImage) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    images.supprimer(ancienneImage);
                } else {
                    images.supprimer(nouvelleImage);
                }
            }
        });
    }

    private void supprimerImageApresValidation(String ancienneImage) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                images.supprimer(ancienneImage);
            }
        });
    }
}
