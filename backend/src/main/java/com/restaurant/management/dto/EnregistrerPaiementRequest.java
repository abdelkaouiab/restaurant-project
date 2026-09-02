package com.restaurant.management.dto;

import com.restaurant.management.enums.ModePaiement;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record EnregistrerPaiementRequest(
        @NotNull
        @DecimalMin(value = "0.01")
        @DecimalMax(value = "9999999999.99")
        @Digits(integer = 10, fraction = 2)
        BigDecimal montant,
        @NotNull ModePaiement mode
) {
}
