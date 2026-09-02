package com.restaurant.management.service;

import com.restaurant.management.dto.CreerReservationRequest;
import com.restaurant.management.dto.ReservationResponse;
import com.restaurant.management.dto.PageResponse;
import com.restaurant.management.entity.*;
import com.restaurant.management.enums.StatutReservation;
import com.restaurant.management.exception.BusinessConflictException;
import com.restaurant.management.exception.ResourceNotFoundException;
import com.restaurant.management.mapper.ReservationMapper;
import com.restaurant.management.repository.*;
import com.restaurant.management.security.RestaurantUserPrincipal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {
    private static final List<StatutReservation> STATUTS_OCCUPANTS = List.of(
            StatutReservation.EN_ATTENTE, StatutReservation.CONFIRMEE, StatutReservation.ARRIVEE
    );
    private final ReservationRepository repository;
    private final TableRestaurantRepository tableRepository;
    private final ClientRepository clientRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CommandeRepository commandeRepository;
    private final ReservationMapper mapper;
    private final JournalModificationService journal;
    private final ZoneId fuseauHoraireRestaurant;

    public ReservationService(ReservationRepository repository, TableRestaurantRepository tableRepository,
            ClientRepository clientRepository, UtilisateurRepository utilisateurRepository,
            CommandeRepository commandeRepository, ReservationMapper mapper, JournalModificationService journal,
            @Value("${app.restaurant.time-zone}") String fuseauHoraireRestaurant) {
        this.repository = repository; this.tableRepository = tableRepository;
        this.clientRepository = clientRepository; this.utilisateurRepository = utilisateurRepository;
        this.commandeRepository = commandeRepository; this.mapper = mapper; this.journal = journal;
        this.fuseauHoraireRestaurant = ZoneId.of(fuseauHoraireRestaurant);
    }

    @Transactional
    @PreAuthorize("hasAuthority('RESERVATION_GERER')")
    public ReservationResponse creer(CreerReservationRequest request) {
        TableRestaurant table = tableRepository.findByIdAvecVerrou(request.tableId())
                .orElseThrow(ResourceNotFoundException::new);
        if (!table.isActif()) throw new BusinessConflictException("La table n'est pas active.");
        if (request.nombrePersonnes() > table.getCapacite())
            throw new BusinessConflictException("La capacité de la table est insuffisante.");
        Client client = clientRepository.findById(request.clientId()).orElseThrow(ResourceNotFoundException::new);
        if (!client.isActif()) throw new BusinessConflictException("Le client n'est pas actif.");
        var fin = request.debut().plus(Reservation.DUREE);
        if (repository.countConflits(table.getId(), request.debut(), fin, STATUTS_OCCUPANTS) > 0)
            throw new BusinessConflictException("Ce créneau est déjà réservé pour cette table.");
        Utilisateur auteur = utilisateurRepository.findById(principal().getUtilisateurId())
                .orElseThrow(ResourceNotFoundException::new);
        Reservation reservation = repository.saveAndFlush(new Reservation(
                genererNumero(), table, client, auteur, request.nombrePersonnes(), request.debut(), request.remarques()
        ));
        journal.journaliser("RESERVATION_CREATION", "RESERVATION", reservation.getId());
        return mapper.versResponse(reservation);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('RESERVATION_GERER')")
    public PageResponse<ReservationResponse> lister(
            LocalDate date,
            StatutReservation statut,
            int page,
            int taille
    ) {
        Instant debutJournee = date == null
                ? null
                : date.atStartOfDay(fuseauHoraireRestaurant).toInstant();
        Instant finJournee = date == null
                ? null
                : date.plusDays(1).atStartOfDay(fuseauHoraireRestaurant).toInstant();
        Specification<Reservation> filtres = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (statut != null) predicates.add(cb.equal(root.get("statut"), statut));
            if (debutJournee != null) predicates.add(cb.greaterThanOrEqualTo(root.get("debut"), debutJournee));
            if (finJournee != null) predicates.add(cb.lessThan(root.get("debut"), finJournee));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.depuis(repository.findAll(
                        filtres,
                        PageRequest.of(page, taille).withSort(Sort.by("debut").ascending())
                )
                .map(mapper::versResponse));
    }

    @Transactional
    @PreAuthorize("hasAuthority('RESERVATION_GERER')")
    public ReservationResponse transition(Long id, String action) {
        Reservation reservation = trouver(id);
        tableRepository.findByIdAvecVerrou(reservation.getTable().getId()).orElseThrow(ResourceNotFoundException::new);
        executer(() -> {
            switch (action) {
                case "confirmation" -> reservation.confirmer();
                case "arrivee" -> reservation.enregistrerArrivee();
                case "terminaison" -> reservation.terminer();
                case "annulation" -> reservation.annuler();
                case "absence" -> reservation.marquerAbsente();
                default -> throw new IllegalArgumentException("Action inconnue.");
            }
        });
        repository.flush();
        journal.journaliser("RESERVATION_" + action.toUpperCase(), "RESERVATION", id);
        return mapper.versResponse(reservation);
    }

    @Transactional
    @PreAuthorize("hasAuthority('RESERVATION_GERER') and hasAuthority('COMMANDE_GERER')")
    public ReservationResponse creerCommande(Long id) {
        Reservation reservation = trouver(id);
        tableRepository.findByIdAvecVerrou(reservation.getTable().getId()).orElseThrow(ResourceNotFoundException::new);
        if (reservation.getStatut() != StatutReservation.ARRIVEE)
            throw new BusinessConflictException("La commande exige l'arrivée préalable du client.");
        if (commandeRepository.existsByReservationId(id))
            throw new BusinessConflictException("Une commande existe déjà pour cette réservation.");
        Utilisateur auteur = utilisateurRepository.findById(principal().getUtilisateurId())
                .orElseThrow(ResourceNotFoundException::new);
        Commande commande = commandeRepository.saveAndFlush(
                new Commande(genererNumeroCommande(), reservation, auteur)
        );
        journal.journaliser("RESERVATION_COMMANDE_CREATION", "COMMANDE", commande.getId());
        return mapper.versResponse(reservation);
    }

    private Reservation trouver(Long id) {
        return repository.findAvecDetailsById(id).orElseThrow(ResourceNotFoundException::new);
    }
    private void executer(Runnable action) {
        try { action.run(); }
        catch (IllegalStateException | IllegalArgumentException e) { throw new BusinessConflictException(e.getMessage()); }
    }
    private String genererNumero() {
        String n; do { n = "RES-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 12).toUpperCase(); } while (repository.existsByNumero(n));
        return n;
    }
    private String genererNumeroCommande() {
        String n; do { n = "CMD-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 12).toUpperCase(); } while (commandeRepository.existsByNumero(n));
        return n;
    }
    private RestaurantUserPrincipal principal() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof RestaurantUserPrincipal p))
            throw new AccessDeniedException("Authentification requise.");
        return p;
    }
}
