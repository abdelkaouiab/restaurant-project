package com.restaurant.management.dto;

import com.restaurant.management.enums.ModePaiement;
import com.restaurant.management.enums.StatutPaiement;

import java.math.BigDecimal;
import java.time.Instant;

public record PaiementHistoriqueResponse(
        Long id,
        String numeroCommande,
        BigDecimal montant,
        ModePaiement mode,
        StatutPaiement statut,
        Instant creeLe
) {
}
