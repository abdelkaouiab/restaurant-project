package com.restaurant.management.dto;

import com.restaurant.management.enums.UniteMesure;

import java.math.BigDecimal;

public record LigneReceptionAchatResponse(
        Long id,
        Long ligneAchatId,
        Long ingredientId,
        String ingredient,
        UniteMesure unite,
        BigDecimal quantite
) {
}
