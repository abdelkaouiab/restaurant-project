package com.restaurant.management.service;

import com.restaurant.management.dto.ClientRequest;
import com.restaurant.management.dto.ClientResponse;
import com.restaurant.management.dto.ModifierActifRequest;
import com.restaurant.management.dto.PageResponse;
import com.restaurant.management.entity.Client;
import com.restaurant.management.exception.ResourceNotFoundException;
import com.restaurant.management.mapper.ReferentielMapper;
import com.restaurant.management.repository.ClientRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Map;

@Service
public class ClientService {

    private static final Map<String, String> TRI = Map.of(
            "nom", "nom", "prenom", "prenom", "email", "email", "creeLe", "creeLe"
    );
    private final ClientRepository repository;
    private final ReferentielMapper mapper;
    private final JournalModificationService journal;

    public ClientService(ClientRepository repository, ReferentielMapper mapper, JournalModificationService journal) {
        this.repository = repository;
        this.mapper = mapper;
        this.journal = journal;
    }

    @Transactional
    @PreAuthorize("hasAuthority('CLIENT_GERER')")
    public ClientResponse creer(ClientRequest request) {
        return mapper.versResponse(repository.save(
                new Client(request.nom(), request.prenom(), request.email(), request.telephone())
        ));
    }

    @Transactional
    @PreAuthorize("hasAuthority('CLIENT_GERER')")
    public ClientResponse modifier(Long id, ClientRequest request) {
        Client client = trouver(id);
        client.modifier(request.nom(), request.prenom(), request.email(), request.telephone());
        return mapper.versResponse(client);
    }

    @Transactional
    @PreAuthorize("hasAuthority('CLIENT_GERER')")
    public ClientResponse modifierActif(Long id, ModifierActifRequest request) {
        Client client = trouver(id);
        client.setActif(request.actif());
        if (!request.actif()) journal.journaliser("CLIENT_DESACTIVATION", "CLIENT", id);
        return mapper.versResponse(client);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CLIENT_CONSULTER')")
    public ClientResponse obtenir(Long id) {
        return mapper.versResponse(trouver(id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CLIENT_CONSULTER')")
    public PageResponse<ClientResponse> rechercher(
            String recherche, Boolean actif, int page, int taille, String tri, String direction
    ) {
        String terme = RechercheServiceSupport.terme(recherche);
        Specification<Client> specification = (root, query, cb) -> {
            var p = new ArrayList<Predicate>();
            if (actif != null) p.add(cb.equal(root.get("actif"), actif));
            if (terme != null) {
                String motif = "%" + terme + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("nom")), motif),
                        cb.like(cb.lower(root.get("prenom")), motif),
                        cb.like(cb.lower(root.get("email")), motif),
                        cb.like(cb.lower(root.get("telephone")), motif)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<ClientResponse> resultat = repository.findAll(
                specification, RechercheServiceSupport.pageable(page, taille, tri, direction, TRI)
        ).map(mapper::versResponse);
        return PageResponse.depuis(resultat);
    }

    private Client trouver(Long id) {
        return repository.findById(id).orElseThrow(ResourceNotFoundException::new);
    }
}
