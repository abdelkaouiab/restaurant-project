package com.restaurant.management.mapper;

import com.restaurant.management.dto.CommandeResponse;
import com.restaurant.management.dto.LigneCommandeResponse;
import com.restaurant.management.entity.Commande;
import com.restaurant.management.entity.LigneCommande;
import org.springframework.stereotype.Component;

@Component
public class CommandeMapper {

    private final ReferentielMapper referentielMapper;

    public CommandeMapper(ReferentielMapper referentielMapper) {
        this.referentielMapper = referentielMapper;
    }

    public CommandeResponse versResponse(Commande commande) {
        return new CommandeResponse(
                commande.getId(),
                commande.getNumero(),
                commande.getStatut(),
                referentielMapper.versResponse(commande.getTable()),
                commande.getClient() == null ? null : referentielMapper.versResponse(commande.getClient()),
                commande.getCreePar().getId(),
                commande.getCreePar().getEmail(),
                commande.getLignes().stream().map(this::versResponse).toList(),
                commande.getMontantTotal(),
                commande.getConfirmeeLe(),
                commande.getAnnuleeLe(),
                commande.getCreeLe(),
                commande.getModifieLe(),
                commande.getVersion()
        );
    }

    private LigneCommandeResponse versResponse(LigneCommande ligne) {
        return new LigneCommandeResponse(
                ligne.getId(),
                ligne.getProduit().getId(),
                ligne.getProduit().getNom(),
                ligne.getQuantite(),
                ligne.getPrixUnitaire(),
                ligne.getSousTotal(),
                ligne.getVersion()
        );
    }
}
