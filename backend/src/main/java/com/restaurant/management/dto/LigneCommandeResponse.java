package com.restaurant.management.dto;

import java.math.BigDecimal;

public record LigneCommandeResponse(
        Long id,
        Long produitId,
        String produitNom,
        int quantite,
        BigDecimal prixUnitaire,
        BigDecimal sousTotal,
        long version
) {
}
