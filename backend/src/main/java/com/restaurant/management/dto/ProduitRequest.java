package com.restaurant.management.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProduitRequest(
        @NotBlank @Size(max = 150) String nom,
        @Size(max = 1000) String description,
        @NotNull @DecimalMin(value = "0.01") @DecimalMax(value = "9999999999.99")
        @Digits(integer = 10, fraction = 2) BigDecimal prix,
        @NotNull @Positive Long categorieId,
        Boolean disponible
) {
}
