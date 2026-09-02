package com.restaurant.management.service;

import com.restaurant.management.entity.JournalActivite;
import com.restaurant.management.entity.Utilisateur;
import com.restaurant.management.enums.ResultatActivite;
import com.restaurant.management.repository.JournalActiviteRepository;
import com.restaurant.management.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class JournalAuthentificationService {

    private final JournalActiviteRepository journalActiviteRepository;
    private final UtilisateurRepository utilisateurRepository;

    public JournalAuthentificationService(
            JournalActiviteRepository journalActiviteRepository,
            UtilisateurRepository utilisateurRepository
    ) {
        this.journalActiviteRepository = journalActiviteRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void journaliserConnexionReussie(Long utilisateurId, String remoteAddress, UUID correlationId) {
        save(utilisateurId, "CONNEXION", ResultatActivite.SUCCES, remoteAddress, correlationId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void journaliserConnexionEchouee(String remoteAddress, UUID correlationId) {
        save(null, "CONNEXION", ResultatActivite.ECHEC, remoteAddress, correlationId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void journaliserDeconnexion(Long utilisateurId, String remoteAddress, UUID correlationId) {
        save(utilisateurId, "DECONNEXION", ResultatActivite.SUCCES, remoteAddress, correlationId);
    }

    private void save(
            Long utilisateurId,
            String action,
            ResultatActivite resultat,
            String remoteAddress,
            UUID correlationId
    ) {
        Utilisateur utilisateur = utilisateurId == null
                ? null
                : utilisateurRepository.findById(utilisateurId).orElse(null);
        JournalActivite activite = new JournalActivite(
                utilisateur,
                action,
                "AUTHENTIFICATION",
                null,
                resultat,
                correlationId
        );
        activite.setAdresseIp(remoteAddress);
        journalActiviteRepository.save(activite);
    }
}
