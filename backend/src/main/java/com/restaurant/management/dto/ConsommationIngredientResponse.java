package com.restaurant.management.dto;

import com.restaurant.management.enums.UniteMesure;

import java.math.BigDecimal;

public record ConsommationIngredientResponse(
        Long ingredientId,
        String ingredient,
        BigDecimal quantite,
        UniteMesure unite
) {
}
