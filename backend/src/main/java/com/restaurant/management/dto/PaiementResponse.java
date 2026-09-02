package com.restaurant.management.dto;

import com.restaurant.management.enums.ModePaiement;
import com.restaurant.management.enums.StatutCommande;
import com.restaurant.management.enums.StatutPaiement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaiementResponse(
        Long id,
        UUID cleIdempotence,
        String numeroCommande,
        BigDecimal montant,
        ModePaiement mode,
        StatutPaiement statut,
        Instant creeLe,
        BigDecimal montantPaye,
        BigDecimal montantRestant,
        StatutCommande statutCommande,
        FactureResponse facture
) {
}
