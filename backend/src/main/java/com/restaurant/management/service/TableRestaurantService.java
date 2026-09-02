package com.restaurant.management.service;

import com.restaurant.management.dto.ModifierActifRequest;
import com.restaurant.management.dto.PageResponse;
import com.restaurant.management.dto.TableRestaurantRequest;
import com.restaurant.management.dto.TableRestaurantOperationnelleResponse;
import com.restaurant.management.entity.Commande;
import com.restaurant.management.entity.Reservation;
import com.restaurant.management.entity.TableRestaurant;
import com.restaurant.management.exception.BusinessConflictException;
import com.restaurant.management.exception.ResourceNotFoundException;
import com.restaurant.management.mapper.TableRestaurantOperationnelleMapper;
import com.restaurant.management.repository.CommandeRepository;
import com.restaurant.management.repository.ReservationRepository;
import com.restaurant.management.repository.TableRestaurantRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TableRestaurantService {

    private static final Map<String, String> TRI = Map.of(
            "numero", "numero", "capacite", "capacite", "creeLe", "creeLe"
    );
    private final TableRestaurantRepository repository;
    private final CommandeRepository commandeRepository;
    private final ReservationRepository reservationRepository;
    private final TableRestaurantOperationnelleMapper mapper;
    private final JournalModificationService journal;

    public TableRestaurantService(
            TableRestaurantRepository repository,
            CommandeRepository commandeRepository,
            ReservationRepository reservationRepository,
            TableRestaurantOperationnelleMapper mapper,
            JournalModificationService journal
    ) {
        this.repository = repository;
        this.commandeRepository = commandeRepository;
        this.reservationRepository = reservationRepository;
        this.mapper = mapper;
        this.journal = journal;
    }

    @Transactional
    @PreAuthorize("hasAuthority('TABLE_GERER')")
    public TableRestaurantOperationnelleResponse creer(TableRestaurantRequest request) {
        verifierNumero(request.numero(), null);
        return versResponse(repository.save(new TableRestaurant(request.numero(), request.capacite())));
    }

    @Transactional
    @PreAuthorize("hasAuthority('TABLE_GERER')")
    public TableRestaurantOperationnelleResponse modifier(Long id, TableRestaurantRequest request) {
        TableRestaurant table = trouver(id);
        verifierNumero(request.numero(), id);
        table.modifier(request.numero(), request.capacite());
        return versResponse(table);
    }

    @Transactional
    @PreAuthorize("hasAuthority('TABLE_GERER')")
    public TableRestaurantOperationnelleResponse modifierActif(Long id, ModifierActifRequest request) {
        TableRestaurant table = trouver(id);
        table.setActif(request.actif());
        if (!request.actif()) journal.journaliser("TABLE_DESACTIVATION", "TABLE_RESTAURANT", id);
        return versResponse(table);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TABLE_CONSULTER')")
    public TableRestaurantOperationnelleResponse obtenir(Long id) {
        return versResponse(trouver(id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TABLE_CONSULTER')")
    public PageResponse<TableRestaurantOperationnelleResponse> rechercher(
            String recherche, Boolean actif, int page, int taille, String tri, String direction
    ) {
        String terme = RechercheServiceSupport.terme(recherche);
        Specification<TableRestaurant> specification = (root, query, cb) -> {
            var p = new ArrayList<Predicate>();
            if (actif != null) p.add(cb.equal(root.get("actif"), actif));
            if (terme != null) {
                try {
                    p.add(cb.equal(root.get("numero"), Integer.valueOf(terme)));
                } catch (NumberFormatException exception) {
                    p.add(cb.disjunction());
                }
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<TableRestaurant> tables = repository.findAll(
                specification, RechercheServiceSupport.pageable(page, taille, tri, direction, TRI)
        );
        Situations situations = chargerSituations(tables.getContent());
        return PageResponse.depuis(tables.map(table -> mapper.versResponse(
                table,
                situations.commandes().get(table.getId()),
                situations.reservations().get(table.getId())
        )));
    }

    private TableRestaurantOperationnelleResponse versResponse(TableRestaurant table) {
        Situations situations = chargerSituations(List.of(table));
        return mapper.versResponse(
                table,
                situations.commandes().get(table.getId()),
                situations.reservations().get(table.getId())
        );
    }

    private Situations chargerSituations(Collection<TableRestaurant> tables) {
        List<Long> ids = tables.stream().map(TableRestaurant::getId).toList();
        if (ids.isEmpty()) return new Situations(Map.of(), Map.of());

        Map<Long, Commande> commandes = new LinkedHashMap<>();
        commandeRepository.findActivesParTableIds(ids, DisponibiliteTableSupport.COMMANDES_OCCUPANTES)
                .forEach(commande -> commandes.putIfAbsent(commande.getTable().getId(), commande));

        Map<Long, Reservation> reservations = new LinkedHashMap<>();
        reservationRepository.findEnCoursParTableIds(
                ids, DisponibiliteTableSupport.RESERVATIONS_OCCUPANTES, Instant.now()
        ).forEach(reservation -> reservations.putIfAbsent(reservation.getTable().getId(), reservation));
        return new Situations(commandes, reservations);
    }

    private record Situations(
            Map<Long, Commande> commandes,
            Map<Long, Reservation> reservations
    ) {
    }

    private TableRestaurant trouver(Long id) {
        return repository.findById(id).orElseThrow(ResourceNotFoundException::new);
    }

    private void verifierNumero(int numero, Long idExclu) {
        boolean existe = idExclu == null
                ? repository.existsByNumero(numero)
                : repository.existsByNumeroAndIdNot(numero, idExclu);
        if (existe) throw new BusinessConflictException("Ce numéro de table est déjà utilisé.");
    }
}
