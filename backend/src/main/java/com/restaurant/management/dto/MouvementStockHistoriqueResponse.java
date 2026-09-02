package com.restaurant.management.dto;

import com.restaurant.management.enums.TypeMouvement;

import java.math.BigDecimal;
import java.time.Instant;

public record MouvementStockHistoriqueResponse(
        Long id,
        Long ingredientId,
        String ingredient,
        TypeMouvement type,
        BigDecimal quantite,
        BigDecimal variation,
        BigDecimal quantiteApres,
        String motif,
        Instant creeLe
) {
}
