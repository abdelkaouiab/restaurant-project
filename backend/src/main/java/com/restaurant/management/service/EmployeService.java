package com.restaurant.management.service;

import com.restaurant.management.dto.EmployeRequest;
import com.restaurant.management.dto.EmployeResponse;
import com.restaurant.management.dto.ModifierActifRequest;
import com.restaurant.management.dto.PageResponse;
import com.restaurant.management.entity.Employe;
import com.restaurant.management.entity.Role;
import com.restaurant.management.entity.Utilisateur;
import com.restaurant.management.enums.NomRole;
import com.restaurant.management.exception.BusinessConflictException;
import com.restaurant.management.exception.ResourceNotFoundException;
import com.restaurant.management.mapper.ReferentielMapper;
import com.restaurant.management.repository.EmployeRepository;
import com.restaurant.management.repository.RoleRepository;
import com.restaurant.management.repository.UtilisateurRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Map;

@Service
public class EmployeService {

    private static final Map<String, String> TRI = Map.of(
            "matricule", "matricule", "nom", "nom", "prenom", "prenom",
            "poste", "poste", "creeLe", "creeLe"
    );

    private final EmployeRepository repository;
    private final UtilisateurRepository utilisateurRepository;
    private final RoleRepository roleRepository;
    private final ReferentielMapper mapper;
    private final JournalModificationService journal;

    public EmployeService(
            EmployeRepository repository,
            UtilisateurRepository utilisateurRepository,
            RoleRepository roleRepository,
            ReferentielMapper mapper,
            JournalModificationService journal
    ) {
        this.repository = repository;
        this.utilisateurRepository = utilisateurRepository;
        this.roleRepository = roleRepository;
        this.mapper = mapper;
        this.journal = journal;
    }

    @Transactional
    @PreAuthorize("hasAuthority('EMPLOYE_GERER')")
    public EmployeResponse creer(EmployeRequest request) {
        verifierMatricule(request.matricule(), null);
        Employe employe = new Employe(request.matricule(), request.nom(), request.prenom(),
                request.poste(), request.dateEmbauche());
        employe.setTelephone(normaliserNullable(request.telephone()));
        return mapper.versResponse(repository.save(employe));
    }

    @Transactional
    @PreAuthorize("hasAuthority('EMPLOYE_GERER')")
    public EmployeResponse modifier(Long id, EmployeRequest request) {
        Employe employe = trouver(id);
        verifierMatricule(request.matricule(), id);
        employe.modifier(request.matricule(), request.nom(), request.prenom(),
                normaliserNullable(request.telephone()), request.poste(), request.dateEmbauche());
        return mapper.versResponse(employe);
    }

    @Transactional
    @PreAuthorize("hasAuthority('EMPLOYE_GERER')")
    public EmployeResponse modifierActif(Long id, ModifierActifRequest request) {
        Employe employe = trouver(id);
        if (!request.actif() && employe.isActif()) {
            protegerDernierAdministrateur(employe);
        }
        employe.setActif(request.actif());
        if (!request.actif()) {
            journal.journaliser("EMPLOYE_DESACTIVATION", "EMPLOYE", id);
        }
        return mapper.versResponse(employe);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('EMPLOYE_CONSULTER')")
    public EmployeResponse obtenir(Long id) {
        return mapper.versResponse(trouver(id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('EMPLOYE_CONSULTER')")
    public PageResponse<EmployeResponse> rechercher(
            String recherche, Boolean actif, int page, int taille, String tri, String direction
    ) {
        String terme = RechercheServiceSupport.terme(recherche);
        Specification<Employe> specification = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (actif != null) predicates.add(cb.equal(root.get("actif"), actif));
            if (terme != null) {
                String motif = "%" + terme + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("matricule")), motif),
                        cb.like(cb.lower(root.get("nom")), motif),
                        cb.like(cb.lower(root.get("prenom")), motif),
                        cb.like(cb.lower(root.get("poste")), motif)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<EmployeResponse> resultat = repository.findAll(
                specification, RechercheServiceSupport.pageable(page, taille, tri, direction, TRI)
        ).map(mapper::versResponse);
        return PageResponse.depuis(resultat);
    }

    private Employe trouver(Long id) {
        return repository.findById(id).orElseThrow(ResourceNotFoundException::new);
    }

    private void verifierMatricule(String matricule, Long idExclu) {
        boolean existe = idExclu == null
                ? repository.existsByMatricule(matricule.strip())
                : repository.existsByMatriculeAndIdNot(matricule.strip(), idExclu);
        if (existe) throw new BusinessConflictException("Ce matricule est déjà utilisé.");
    }

    private void protegerDernierAdministrateur(Employe employe) {
        Utilisateur utilisateur = utilisateurRepository.findWithEmployeAndRolesByEmployeId(employe.getId())
                .orElse(null);
        if (utilisateur == null || !utilisateur.isActif()
                || utilisateur.getRoles().stream().map(Role::getNom)
                .noneMatch(NomRole.ADMINISTRATEUR::equals)) {
            return;
        }
        roleRepository.findByNomForUpdate(NomRole.ADMINISTRATEUR)
                .orElseThrow(ResourceNotFoundException::new);
        if (utilisateurRepository.countAdministrateursActifs(NomRole.ADMINISTRATEUR) <= 1) {
            throw new BusinessConflictException("L'employé du dernier administrateur actif ne peut pas être désactivé.");
        }
    }

    private String normaliserNullable(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.strip();
    }
}
