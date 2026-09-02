package com.restaurant.management.dto;

public record ConsommationProduitResponse(
        Long produitId,
        String produit,
        long quantite
) {
}
