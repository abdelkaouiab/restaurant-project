package com.restaurant.management.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProduitResponse(
        Long id, String nom, String description, BigDecimal prix,
        CategorieResponse categorie, boolean disponible, boolean actif,
        String imageUrl, String imageTypeMime,
        Instant creeLe, Instant modifieLe, long version
) {
}
