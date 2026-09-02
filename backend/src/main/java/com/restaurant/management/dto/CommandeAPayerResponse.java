package com.restaurant.management.dto;

import com.restaurant.management.enums.StatutCommande;

import java.math.BigDecimal;
import java.time.Instant;

public record CommandeAPayerResponse(
        String numeroCommande,
        int table,
        StatutCommande statutCommande,
        BigDecimal montantTotal,
        BigDecimal montantPaye,
        BigDecimal montantRestant,
        Instant heure
) {
}
