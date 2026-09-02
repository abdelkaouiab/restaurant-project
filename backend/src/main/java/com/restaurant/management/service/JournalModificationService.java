package com.restaurant.management.service;

import com.restaurant.management.entity.JournalActivite;
import com.restaurant.management.entity.Utilisateur;
import com.restaurant.management.enums.ResultatActivite;
import com.restaurant.management.repository.JournalActiviteRepository;
import com.restaurant.management.repository.UtilisateurRepository;
import com.restaurant.management.security.RestaurantUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class JournalModificationService {

    private final JournalActiviteRepository journalRepository;
    private final UtilisateurRepository utilisateurRepository;

    public JournalModificationService(
            JournalActiviteRepository journalRepository,
            UtilisateurRepository utilisateurRepository
    ) {
        this.journalRepository = journalRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    public void journaliser(String action, String typeRessource, Long cibleId) {
        JournalActivite entree = new JournalActivite(
                rechercherAuteur(),
                action,
                typeRessource,
                cibleId.toString(),
                ResultatActivite.SUCCES,
                UUID.randomUUID()
        );
        journalRepository.save(entree);
    }

    private Utilisateur rechercherAuteur() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof RestaurantUserPrincipal principal)) {
            return null;
        }
        return utilisateurRepository.findById(principal.getUtilisateurId()).orElse(null);
    }
}
