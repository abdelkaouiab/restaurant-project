package com.restaurant.management.dto;

public record ProduitCuisineResponse(
        String produit,
        int quantite,
        String remarque
) {
}
