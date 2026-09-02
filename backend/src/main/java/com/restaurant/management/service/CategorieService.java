package com.restaurant.management.service;

import com.restaurant.management.dto.CategorieRequest;
import com.restaurant.management.dto.CategorieResponse;
import com.restaurant.management.dto.ModifierActifRequest;
import com.restaurant.management.dto.PageResponse;
import com.restaurant.management.entity.Categorie;
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

import java.util.ArrayList;
import java.util.Map;

@Service
public class CategorieService {

    private static final Map<String, String> TRI = Map.of(
            "nom", "nom", "creeLe", "creeLe", "modifieLe", "modifieLe"
    );
    private final CategorieRepository repository;
    private final ProduitRepository produitRepository;
    private final ReferentielMapper mapper;
    private final JournalModificationService journal;

    public CategorieService(
            CategorieRepository repository,
            ProduitRepository produitRepository,
            ReferentielMapper mapper,
            JournalModificationService journal
    ) {
        this.repository = repository;
        this.produitRepository = produitRepository;
        this.mapper = mapper;
        this.journal = journal;
    }

    @Transactional
    @PreAuthorize("hasAuthority('CATEGORIE_GERER')")
    public CategorieResponse creer(CategorieRequest request) {
        verifierNom(request.nom(), null);
        return mapper.versResponse(repository.save(new Categorie(request.nom(), request.description())));
    }

    @Transactional
    @PreAuthorize("hasAuthority('CATEGORIE_GERER')")
    public CategorieResponse modifier(Long id, CategorieRequest request) {
        Categorie categorie = trouver(id);
        verifierNom(request.nom(), id);
        categorie.modifier(request.nom(), request.description());
        return mapper.versResponse(categorie);
    }

    @Transactional
    @PreAuthorize("hasAuthority('CATEGORIE_GERER')")
    public CategorieResponse modifierActif(Long id, ModifierActifRequest request) {
        Categorie categorie = trouver(id);
        if (!request.actif() && produitRepository.existsByCategorieIdAndActifTrue(id)) {
            throw new BusinessConflictException(
                    "Une catégorie contenant des produits actifs ne peut pas être désactivée."
            );
        }
        categorie.setActif(request.actif());
        if (!request.actif()) journal.journaliser("CATEGORIE_DESACTIVATION", "CATEGORIE", id);
        return mapper.versResponse(categorie);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CATEGORIE_CONSULTER')")
    public CategorieResponse obtenir(Long id) {
        return mapper.versResponse(trouver(id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CATEGORIE_CONSULTER')")
    public PageResponse<CategorieResponse> rechercher(
            String recherche, Boolean actif, int page, int taille, String tri, String direction
    ) {
        String terme = RechercheServiceSupport.terme(recherche);
        Specification<Categorie> specification = (root, query, cb) -> {
            var p = new ArrayList<Predicate>();
            if (actif != null) p.add(cb.equal(root.get("actif"), actif));
            if (terme != null) {
                String motif = "%" + terme + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("nom")), motif),
                        cb.like(cb.lower(root.get("description")), motif)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<CategorieResponse> resultat = repository.findAll(
                specification, RechercheServiceSupport.pageable(page, taille, tri, direction, TRI)
        ).map(mapper::versResponse);
        return PageResponse.depuis(resultat);
    }

    private Categorie trouver(Long id) {
        return repository.findById(id).orElseThrow(ResourceNotFoundException::new);
    }

    private void verifierNom(String nom, Long idExclu) {
        boolean existe = idExclu == null
                ? repository.existsByNomIgnoreCase(nom.strip())
                : repository.existsByNomIgnoreCaseAndIdNot(nom.strip(), idExclu);
        if (existe) throw new BusinessConflictException("Ce nom de catégorie est déjà utilisé.");
    }
}
