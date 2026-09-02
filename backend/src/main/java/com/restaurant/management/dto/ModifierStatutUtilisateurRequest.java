package com.restaurant.management.dto;

import jakarta.validation.constraints.NotNull;

public record ModifierStatutUtilisateurRequest(
        @NotNull Boolean actif
) {
}
