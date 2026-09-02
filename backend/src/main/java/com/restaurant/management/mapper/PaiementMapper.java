package com.restaurant.management.mapper;

import com.restaurant.management.dto.CommandeAPayerResponse;
import com.restaurant.management.dto.FactureResponse;
import com.restaurant.management.dto.PaiementHistoriqueResponse;
import com.restaurant.management.dto.PaiementResponse;
import com.restaurant.management.entity.Commande;
import com.restaurant.management.entity.Facture;
import com.restaurant.management.entity.Paiement;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PaiementMapper {
    public CommandeAPayerResponse versCommandeAPayer(Commande commande, BigDecimal montantPaye) {
        return new CommandeAPayerResponse(
                commande.getNumero(),
                commande.getTable().getNumero(),
                commande.getStatut(),
                commande.getMontantTotal(),
                montantPaye,
                commande.getMontantTotal().subtract(montantPaye),
                commande.getConfirmeeLe()
        );
    }

    public FactureResponse versFacture(Facture facture) {
        return new FactureResponse(
                facture.getNumero(),
                facture.getCommande().getNumero(),
                facture.getMontantTotal(),
                facture.getEmiseLe()
        );
    }

    public PaiementHistoriqueResponse versHistorique(Paiement paiement) {
        return new PaiementHistoriqueResponse(
                paiement.getId(),
                paiement.getCommande().getNumero(),
                paiement.getMontant(),
                paiement.getMode(),
                paiement.getStatut(),
                paiement.getCreeLe()
        );
    }

    public PaiementResponse versPaiement(
            Paiement paiement,
            BigDecimal montantPaye,
            Facture facture
    ) {
        Commande commande = paiement.getCommande();
        return new PaiementResponse(
                paiement.getId(),
                paiement.getCleIdempotence(),
                commande.getNumero(),
                paiement.getMontant(),
                paiement.getMode(),
                paiement.getStatut(),
                paiement.getCreeLe(),
                montantPaye,
                commande.getMontantTotal().subtract(montantPaye),
                commande.getStatut(),
                facture == null ? null : versFacture(facture)
        );
    }
}
