package com.restaurant.management.dto;

import com.restaurant.management.enums.StatutCommande;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CommandeResponse(
        Long id,
        String numero,
        StatutCommande statut,
        TableRestaurantResponse table,
        ClientResponse client,
        Long creeParId,
        String creeParEmail,
        List<LigneCommandeResponse> lignes,
        BigDecimal montantTotal,
        Instant confirmeeLe,
        Instant annuleeLe,
        Instant creeLe,
        Instant modifieLe,
        long version
) {
}
