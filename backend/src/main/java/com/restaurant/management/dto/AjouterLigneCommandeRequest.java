package com.restaurant.management.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AjouterLigneCommandeRequest(
        @NotNull @Positive Long produitId,
        @Positive @Max(999) int quantite,
        @Size(max = 500) String remarque,
        @Null BigDecimal prix,
        @Null BigDecimal prixUnitaire,
        @Null BigDecimal sousTotal,
        @Null BigDecimal montantTotal,
        @Null BigDecimal total
) {
    public AjouterLigneCommandeRequest(
            Long produitId,
            int quantite,
            BigDecimal prix,
            BigDecimal prixUnitaire,
            BigDecimal sousTotal,
            BigDecimal montantTotal,
            BigDecimal total
    ) {
        this(produitId, quantite, null, prix, prixUnitaire, sousTotal, montantTotal, total);
    }
}
