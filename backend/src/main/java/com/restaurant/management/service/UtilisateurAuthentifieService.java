package com.restaurant.management.service;

import com.restaurant.management.dto.EmployeAuthentifieResponse;
import com.restaurant.management.dto.UtilisateurAuthentifieResponse;
import com.restaurant.management.entity.Permission;
import com.restaurant.management.entity.Role;
import com.restaurant.management.entity.Utilisateur;
import com.restaurant.management.exception.AuthenticationFailedException;
import com.restaurant.management.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.TreeSet;

@Service
public class UtilisateurAuthentifieService {

    private final UtilisateurRepository utilisateurRepository;

    public UtilisateurAuthentifieService(UtilisateurRepository utilisateurRepository) {
        this.utilisateurRepository = utilisateurRepository;
    }

    @Transactional(readOnly = true)
    public UtilisateurAuthentifieResponse getById(Long utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findWithEmployeAndRolesById(utilisateurId)
                .orElseThrow(AuthenticationFailedException::new);

        if (!utilisateur.isActif() || !utilisateur.getEmploye().isActif()) {
            throw new AuthenticationFailedException();
        }

        Set<String> roles = new TreeSet<>();
        Set<String> permissions = new TreeSet<>();
        utilisateur.getRoles().stream()
                .filter(Role::isActif)
                .forEach(role -> {
                    roles.add(role.getNom().name());
                    role.getPermissions().stream()
                            .filter(Permission::isActif)
                            .map(permission -> permission.getNom().name())
                            .forEach(permissions::add);
                });

        return new UtilisateurAuthentifieResponse(
                utilisateur.getId(),
                utilisateur.getEmail(),
                new EmployeAuthentifieResponse(
                        utilisateur.getEmploye().getId(),
                        utilisateur.getEmploye().getMatricule(),
                        utilisateur.getEmploye().getNom(),
                        utilisateur.getEmploye().getPrenom(),
                        utilisateur.getEmploye().getPoste()
                ),
                Set.copyOf(roles),
                Set.copyOf(permissions)
        );
    }
}
