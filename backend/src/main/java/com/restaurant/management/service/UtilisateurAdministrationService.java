package com.restaurant.management.service;

import com.restaurant.management.dto.CreerUtilisateurRequest;
import com.restaurant.management.dto.ModifierRolesUtilisateurRequest;
import com.restaurant.management.dto.ModifierStatutUtilisateurRequest;
import com.restaurant.management.dto.ModifierUtilisateurRequest;
import com.restaurant.management.dto.PageResponse;
import com.restaurant.management.dto.ReinitialiserMotDePasseRequest;
import com.restaurant.management.dto.UtilisateurAdministrationResponse;
import com.restaurant.management.entity.Employe;
import com.restaurant.management.entity.Role;
import com.restaurant.management.entity.Utilisateur;
import com.restaurant.management.enums.NomRole;
import com.restaurant.management.exception.BusinessConflictException;
import com.restaurant.management.exception.InvalidSortFieldException;
import com.restaurant.management.exception.ResourceNotFoundException;
import com.restaurant.management.mapper.UtilisateurMapper;
import com.restaurant.management.repository.EmployeRepository;
import com.restaurant.management.repository.RoleRepository;
import com.restaurant.management.repository.UtilisateurRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UtilisateurAdministrationService {

    private static final Map<String, String> CHAMPS_TRI = Map.of(
            "email", "email",
            "creeLe", "creeLe",
            "modifieLe", "modifieLe",
            "nomEmploye", "employe.nom"
    );

    private final UtilisateurRepository utilisateurRepository;
    private final EmployeRepository employeRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UtilisateurMapper mapper;
    private final JournalModificationService journalService;

    public UtilisateurAdministrationService(
            UtilisateurRepository utilisateurRepository,
            EmployeRepository employeRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            UtilisateurMapper mapper,
            JournalModificationService journalService
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.employeRepository = employeRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
        this.journalService = journalService;
    }

    @Transactional
    @PreAuthorize("hasAuthority('UTILISATEUR_GERER') and hasAuthority('ROLE_GERER')")
    public UtilisateurAdministrationResponse creer(CreerUtilisateurRequest request) {
        verifierEmailDisponible(request.email(), null);
        if (utilisateurRepository.existsByEmployeId(request.employeId())) {
            throw new BusinessConflictException("Cet employé possède déjà un compte.");
        }

        Employe employe = employeRepository.findById(request.employeId())
                .orElseThrow(ResourceNotFoundException::new);
        List<Role> roles = rechercherRoles(request.roles());
        Utilisateur utilisateur = new Utilisateur(
                request.email(),
                passwordEncoder.encode(request.motDePasse()),
                employe
        );
        utilisateur.remplacerRoles(roles);
        Utilisateur sauvegarde = utilisateurRepository.save(utilisateur);
        journalService.journaliser("UTILISATEUR_CREATION", "UTILISATEUR", sauvegarde.getId());
        return mapper.versAdministrationResponse(sauvegarde);
    }

    @Transactional
    @PreAuthorize("hasAuthority('UTILISATEUR_GERER')")
    public UtilisateurAdministrationResponse modifier(Long id, ModifierUtilisateurRequest request) {
        Utilisateur utilisateur = rechercherUtilisateur(id);
        verifierEmailDisponible(request.email(), id);
        utilisateur.modifierEmail(request.email());
        journalService.journaliser("UTILISATEUR_MODIFICATION", "UTILISATEUR", id);
        return mapper.versAdministrationResponse(utilisateur);
    }

    @Transactional
    @PreAuthorize("hasAuthority('ROLE_GERER')")
    public UtilisateurAdministrationResponse modifierRoles(Long id, ModifierRolesUtilisateurRequest request) {
        Utilisateur utilisateur = rechercherUtilisateur(id);
        Set<NomRole> rolesDemandes = Set.copyOf(request.roles());
        boolean administrateurActuel = possedeRole(utilisateur, NomRole.ADMINISTRATEUR);
        boolean conserveAdministration = rolesDemandes.contains(NomRole.ADMINISTRATEUR);
        if (administrateurActuel && utilisateur.isActif() && utilisateur.getEmploye().isActif()
                && !conserveAdministration) {
            verifierQuIlResteUnAdministrateur();
        }

        utilisateur.remplacerRoles(rechercherRoles(rolesDemandes));
        journalService.journaliser("UTILISATEUR_ROLES_MODIFICATION", "UTILISATEUR", id);
        return mapper.versAdministrationResponse(utilisateur);
    }

    @Transactional
    @PreAuthorize("hasAuthority('UTILISATEUR_GERER')")
    public UtilisateurAdministrationResponse modifierStatut(Long id, ModifierStatutUtilisateurRequest request) {
        Utilisateur utilisateur = rechercherUtilisateur(id);
        if (!request.actif() && utilisateur.isActif()
                && utilisateur.getEmploye().isActif()
                && possedeRole(utilisateur, NomRole.ADMINISTRATEUR)) {
            verifierQuIlResteUnAdministrateur();
        }

        utilisateur.setActif(request.actif());
        journalService.journaliser(
                request.actif() ? "UTILISATEUR_REACTIVATION" : "UTILISATEUR_DESACTIVATION",
                "UTILISATEUR", id
        );
        return mapper.versAdministrationResponse(utilisateur);
    }

    @Transactional
    @PreAuthorize("hasAuthority('UTILISATEUR_GERER')")
    public void reinitialiserMotDePasse(Long id, ReinitialiserMotDePasseRequest request) {
        Utilisateur utilisateur = rechercherUtilisateur(id);
        utilisateur.remplacerMotDePasseHash(passwordEncoder.encode(request.nouveauMotDePasse()));
        journalService.journaliser(
                "UTILISATEUR_MOT_DE_PASSE_REINITIALISATION", "UTILISATEUR", id
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('UTILISATEUR_CONSULTER')")
    public PageResponse<UtilisateurAdministrationResponse> rechercher(
            String recherche,
            Boolean actif,
            int page,
            int taille,
            String tri,
            String direction
    ) {
        String champJpa = CHAMPS_TRI.get(tri);
        if (champJpa == null) {
            throw new InvalidSortFieldException();
        }

        Sort.Direction sens;
        try {
            sens = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException exception) {
            throw new InvalidSortFieldException();
        }

        String terme = recherche == null || recherche.isBlank() ? "" : recherche.strip();
        Pageable pageable = PageRequest.of(page, taille, Sort.by(sens, champJpa));
        Page<UtilisateurAdministrationResponse> resultats = utilisateurRepository
                .rechercher(terme, actif, pageable)
                .map(mapper::versAdministrationResponse);
        return PageResponse.depuis(resultats);
    }

    private Utilisateur rechercherUtilisateur(Long id) {
        return utilisateurRepository.findWithEmployeAndRolesById(id)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private List<Role> rechercherRoles(Set<NomRole> noms) {
        List<Role> roles = roleRepository.findAllByNomIn(noms);
        Set<NomRole> trouves = roles.stream().map(Role::getNom).collect(Collectors.toSet());
        if (trouves.size() != noms.size() || !trouves.containsAll(noms)) {
            throw new ResourceNotFoundException();
        }
        return roles;
    }

    private void verifierEmailDisponible(String email, Long utilisateurIdExclu) {
        boolean existe = utilisateurIdExclu == null
                ? utilisateurRepository.existsByEmailIgnoreCase(email)
                : utilisateurRepository.existsByEmailIgnoreCaseAndIdNot(email, utilisateurIdExclu);
        if (existe) {
            throw new BusinessConflictException("Cette adresse e-mail est déjà utilisée.");
        }
    }

    private boolean possedeRole(Utilisateur utilisateur, NomRole nomRole) {
        return utilisateur.getRoles().stream().anyMatch(role -> role.getNom() == nomRole);
    }

    private void verifierQuIlResteUnAdministrateur() {
        roleRepository.findByNomForUpdate(NomRole.ADMINISTRATEUR)
                .orElseThrow(ResourceNotFoundException::new);
        if (utilisateurRepository.countAdministrateursActifs(NomRole.ADMINISTRATEUR) <= 1) {
            throw new BusinessConflictException("Le dernier administrateur actif ne peut pas être désactivé.");
        }
    }
}
