package com.restaurant.management.service;

import com.restaurant.management.dto.CommandeCuisineResponse;
import com.restaurant.management.entity.Commande;
import com.restaurant.management.entity.NotificationInterne;
import com.restaurant.management.enums.StatutCommande;
import com.restaurant.management.enums.TypeNotificationInterne;
import com.restaurant.management.exception.BusinessConflictException;
import com.restaurant.management.exception.ResourceNotFoundException;
import com.restaurant.management.mapper.CuisineCommandeMapper;
import com.restaurant.management.repository.CommandeRepository;
import com.restaurant.management.repository.NotificationInterneRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class CuisineCommandeService {
    private static final Duration DUREE_HISTORIQUE_PRETES = Duration.ofHours(2);
    private static final List<StatutCommande> STATUTS_ACTIFS =
            List.of(StatutCommande.CONFIRMEE, StatutCommande.EN_PREPARATION);

    private final CommandeRepository commandeRepository;
    private final NotificationInterneRepository notificationRepository;
    private final CuisineCommandeMapper mapper;
    private final JournalModificationService journal;

    public CuisineCommandeService(
            CommandeRepository commandeRepository,
            NotificationInterneRepository notificationRepository,
            CuisineCommandeMapper mapper,
            JournalModificationService journal
    ) {
        this.commandeRepository = commandeRepository;
        this.notificationRepository = notificationRepository;
        this.mapper = mapper;
        this.journal = journal;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CUISINE_COMMANDES_GERER')")
    public List<CommandeCuisineResponse> lister() {
        Instant preteDepuis = Instant.now().minus(DUREE_HISTORIQUE_PRETES);
        return commandeRepository.findPourCuisine(
                        STATUTS_ACTIFS,
                        StatutCommande.PRETE,
                        preteDepuis
                ).stream()
                .map(mapper::versResponse)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('CUISINE_COMMANDES_GERER')")
    public CommandeCuisineResponse demarrer(String numero) {
        Commande commande = trouver(numero);
        executerTransition(commande::demarrerPreparation);
        commandeRepository.flush();
        journal.journaliser("CUISINE_PREPARATION_DEMARREE", "COMMANDE", commande.getId());
        return mapper.versResponse(commande);
    }

    @Transactional
    @PreAuthorize("hasAuthority('CUISINE_COMMANDES_GERER')")
    public CommandeCuisineResponse marquerPrete(String numero) {
        Commande commande = trouver(numero);
        executerTransition(commande::marquerPrete);
        commandeRepository.flush();
        notificationRepository.save(new NotificationInterne(
                commande,
                TypeNotificationInterne.COMMANDE_PRETE,
                "Commande " + commande.getNumero() + " prête pour la table " + commande.getTable().getNumero()
        ));
        journal.journaliser("CUISINE_COMMANDE_PRETE", "COMMANDE", commande.getId());
        return mapper.versResponse(commande);
    }

    private Commande trouver(String numero) {
        return commandeRepository.findAvecDetailsByNumero(numero)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private void executerTransition(Runnable transition) {
        try {
            transition.run();
        } catch (IllegalStateException exception) {
            throw new BusinessConflictException(exception.getMessage());
        }
    }
}
