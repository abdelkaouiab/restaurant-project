package com.restaurant.management.dto;

import com.restaurant.management.enums.StatutCommande;

public record CommandeAssocieeTableResponse(
        Long id,
        String numero,
        StatutCommande statut
) {
}
