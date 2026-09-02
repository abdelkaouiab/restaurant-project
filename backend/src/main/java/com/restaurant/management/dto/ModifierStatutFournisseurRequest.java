package com.restaurant.management.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ModifierStatutFournisseurRequest(
        @NotNull Boolean actif,
        @NotNull @PositiveOrZero Long version
) {
}
