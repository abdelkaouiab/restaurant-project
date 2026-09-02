package com.restaurant.management.mapper;

import com.restaurant.management.dto.EmployeUtilisateurResponse;
import com.restaurant.management.dto.UtilisateurAdministrationResponse;
import com.restaurant.management.entity.Employe;
import com.restaurant.management.entity.Role;
import com.restaurant.management.entity.Utilisateur;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.stream.Collectors;

@Component
public class UtilisateurMapper {

    public UtilisateurAdministrationResponse versAdministrationResponse(Utilisateur utilisateur) {
        Employe employe = utilisateur.getEmploye();
        return new UtilisateurAdministrationResponse(
                utilisateur.getId(),
                utilisateur.getEmail(),
                utilisateur.isActif(),
                new EmployeUtilisateurResponse(
                        employe.getId(),
                        employe.getMatricule(),
                        employe.getNom(),
                        employe.getPrenom()
                ),
                utilisateur.getRoles().stream()
                        .map(Role::getNom)
                        .sorted()
                        .collect(Collectors.toCollection(LinkedHashSet::new)),
                utilisateur.getCreeLe(),
                utilisateur.getModifieLe(),
                utilisateur.getVersion()
        );
    }
}
