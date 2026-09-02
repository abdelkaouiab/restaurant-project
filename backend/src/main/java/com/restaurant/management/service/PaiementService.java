package com.restaurant.management.service;

import com.restaurant.management.dto.CommandeAPayerResponse;
import com.restaurant.management.dto.EnregistrerPaiementRequest;
import com.restaurant.management.dto.FactureResponse;
import com.restaurant.management.dto.PageResponse;
import com.restaurant.management.dto.PaiementHistoriqueResponse;
import com.restaurant.management.dto.PaiementResponse;
import com.restaurant.management.entity.Commande;
import com.restaurant.management.entity.Facture;
import com.restaurant.management.entity.Paiement;
import com.restaurant.management.entity.Utilisateur;
import com.restaurant.management.enums.StatutCommande;
import com.restaurant.management.enums.StatutPaiement;
import com.restaurant.management.enums.StatutReservation;
import com.restaurant.management.exception.BusinessConflictException;
import com.restaurant.management.exception.ResourceNotFoundException;
import com.restaurant.management.mapper.PaiementMapper;
import com.restaurant.management.repository.CommandeRepository;
import com.restaurant.management.repository.FactureRepository;
import com.restaurant.management.repository.PaiementRepository;
import com.restaurant.management.repository.UtilisateurRepository;
import com.restaurant.management.security.RestaurantUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class PaiementService {
    private static final List<StatutCommande> STATUTS_PAYABLES = List.of(StatutCommande.PRETE);

    private final CommandeRepository commandeRepository;
    private final PaiementRepository paiementRepository;
    private final FactureRepository factureRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final PaiementMapper mapper;
    private final FacturePdfService facturePdfService;
    private final JournalModificationService journal;

    public PaiementService(
            CommandeRepository commandeRepository,
            PaiementRepository paiementRepository,
            FactureRepository factureRepository,
            UtilisateurRepository utilisateurRepository,
            PaiementMapper mapper,
            FacturePdfService facturePdfService,
            JournalModificationService journal
    ) {
        this.commandeRepository = commandeRepository;
        this.paiementRepository = paiementRepository;
        this.factureRepository = factureRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.mapper = mapper;
        this.facturePdfService = facturePdfService;
        this.journal = journal;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CAISSE_PAIEMENTS_GERER')")
    public List<CommandeAPayerResponse> listerCommandesAPayer() {
        return commandeRepository.findCommandesAPayer(STATUTS_PAYABLES).stream()
                .map(commande -> mapper.versCommandeAPayer(commande, sommeValidee(commande.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CAISSE_PAIEMENTS_GERER')")
    public PageResponse<PaiementHistoriqueResponse> listerPaiementsDuJour(
            LocalDate date,
            int page,
            int taille
    ) {
        LocalDate jour = date == null ? LocalDate.now(ZoneOffset.UTC) : date;
        var debut = jour.atStartOfDay(ZoneOffset.UTC).toInstant();
        var finExclusive = jour.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        var pageable = PageRequest.of(
                page,
                taille,
                Sort.by(Sort.Order.desc("creeLe"), Sort.Order.desc("id"))
        );
        return PageResponse.depuis(
                paiementRepository.findByStatutAndCreeLeGreaterThanEqualAndCreeLeLessThan(
                        StatutPaiement.VALIDE,
                        debut,
                        finExclusive,
                        pageable
                ).map(mapper::versHistorique)
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CAISSE_PAIEMENTS_GERER')")
    public PageResponse<FactureResponse> listerFacturesRecentes(int page, int taille) {
        var pageable = PageRequest.of(
                page,
                taille,
                Sort.by(Sort.Order.desc("emiseLe"), Sort.Order.desc("id"))
        );
        return PageResponse.depuis(factureRepository.findAllBy(pageable).map(mapper::versFacture));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CAISSE_PAIEMENTS_GERER')")
    public PageResponse<PaiementHistoriqueResponse> listerPaiementsCommande(
            String numeroCommande,
            int page,
            int taille
    ) {
        Commande commande = commandeRepository.findAvecDetailsByNumero(numeroCommande)
                .orElseThrow(ResourceNotFoundException::new);
        var pageable = PageRequest.of(
                page,
                taille,
                Sort.by(Sort.Order.desc("creeLe"), Sort.Order.desc("id"))
        );
        return PageResponse.depuis(
                paiementRepository.findByCommandeIdAndStatut(
                        commande.getId(),
                        StatutPaiement.VALIDE,
                        pageable
                ).map(mapper::versHistorique)
        );
    }

    @Transactional
    @PreAuthorize("hasAuthority('CAISSE_PAIEMENTS_GERER')")
    public PaiementResponse enregistrer(
            String numeroCommande,
            UUID cleIdempotence,
            EnregistrerPaiementRequest request
    ) {
        Paiement existant = paiementRepository.findByCleIdempotence(cleIdempotence).orElse(null);
        if (existant != null) return verifierEtRetourner(existant, numeroCommande, request);

        Commande commande = commandeRepository.findPourPaiementAvecVerrou(numeroCommande)
                .orElseThrow(ResourceNotFoundException::new);

        existant = paiementRepository.findByCleIdempotence(cleIdempotence).orElse(null);
        if (existant != null) return verifierEtRetourner(existant, numeroCommande, request);
        if (!STATUTS_PAYABLES.contains(commande.getStatut())) {
            throw new BusinessConflictException("Cette commande n'est pas payable.");
        }

        BigDecimal montantPayeAvant = sommeValidee(commande.getId());
        BigDecimal restant = commande.getMontantTotal().subtract(montantPayeAvant);
        if (request.montant().compareTo(BigDecimal.ZERO) <= 0
                || request.montant().compareTo(restant) > 0) {
            throw new BusinessConflictException("Le montant doit être positif et ne pas dépasser le restant dû.");
        }

        Utilisateur caissier = utilisateurRepository.findById(principal().getUtilisateurId())
                .orElseThrow(ResourceNotFoundException::new);
        Paiement paiement = paiementRepository.saveAndFlush(new Paiement(
                commande, caissier, cleIdempotence, request.montant(), request.mode()
        ));
        BigDecimal montantPaye = montantPayeAvant.add(paiement.getMontant());
        Facture facture = null;
        if (montantPaye.compareTo(commande.getMontantTotal()) == 0) {
            commande.marquerPayee();
            if (commande.getReservation() != null
                    && commande.getReservation().getStatut() == StatutReservation.ARRIVEE) {
                commande.getReservation().terminer();
            }
            commandeRepository.flush();
            facture = factureRepository.saveAndFlush(new Facture(
                    commande, genererNumeroFacture(), commande.getMontantTotal()
            ));
        }
        journal.journaliser("PAIEMENT_VALIDE", "PAIEMENT", paiement.getId());
        if (facture != null) journal.journaliser("FACTURE_EMISE", "FACTURE", facture.getId());
        return mapper.versPaiement(paiement, montantPaye, facture);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CAISSE_PAIEMENTS_GERER')")
    public FactureResponse obtenirFacture(String numeroCommande) {
        Commande commande = commandeRepository.findAvecDetailsByNumero(numeroCommande)
                .orElseThrow(ResourceNotFoundException::new);
        Facture facture = factureRepository.findByCommandeId(commande.getId())
                .orElseThrow(() -> new BusinessConflictException(
                        "La facture n'est disponible qu'après le paiement complet."
                ));
        return mapper.versFacture(facture);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CAISSE_PAIEMENTS_GERER')")
    public byte[] obtenirFacturePdf(String numeroCommande) {
        Commande commande = commandeRepository.findAvecDetailsByNumero(numeroCommande)
                .orElseThrow(ResourceNotFoundException::new);
        Facture facture = factureRepository.findByCommandeId(commande.getId())
                .orElseThrow(() -> new BusinessConflictException(
                        "La facture n'est disponible qu'après le paiement complet."
                ));
        return facturePdfService.generer(facture, commande);
    }

    private PaiementResponse verifierEtRetourner(
            Paiement paiement,
            String numeroCommande,
            EnregistrerPaiementRequest request
    ) {
        if (!paiement.getCommande().getNumero().equals(numeroCommande)
                || paiement.getMontant().compareTo(request.montant()) != 0
                || paiement.getMode() != request.mode()) {
            throw new BusinessConflictException("La clé d'idempotence a déjà été utilisée.");
        }
        BigDecimal total = sommeValidee(paiement.getCommande().getId());
        Facture facture = factureRepository.findByCommandeId(paiement.getCommande().getId()).orElse(null);
        return mapper.versPaiement(paiement, total, facture);
    }

    private BigDecimal sommeValidee(Long commandeId) {
        return paiementRepository.sommeParCommandeEtStatut(commandeId, StatutPaiement.VALIDE);
    }

    private String genererNumeroFacture() {
        String numero;
        do {
            numero = "FAC-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        } while (factureRepository.existsByNumero(numero));
        return numero;
    }

    private RestaurantUserPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof RestaurantUserPrincipal utilisateur)) {
            throw new AccessDeniedException("Authentification requise.");
        }
        return utilisateur;
    }
}
