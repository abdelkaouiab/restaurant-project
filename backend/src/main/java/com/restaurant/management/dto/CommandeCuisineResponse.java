package com.restaurant.management.dto;

import com.restaurant.management.enums.StatutCommande;

import java.time.Instant;
import java.util.List;

public record CommandeCuisineResponse(
        String numero,
        int table,
        List<ProduitCuisineResponse> produits,
        Instant heure,
        StatutCommande etat
) {
}
