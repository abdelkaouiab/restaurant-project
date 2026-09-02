package com.restaurant.management.dto;

import java.time.LocalDate;
import java.util.List;

public record ConsommationResponse(
        LocalDate debut,
        LocalDate fin,
        List<ConsommationProduitResponse> produits,
        List<ConsommationIngredientResponse> ingredients
) {
}
