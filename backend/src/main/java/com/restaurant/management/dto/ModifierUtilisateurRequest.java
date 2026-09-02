package com.restaurant.management.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModifierUtilisateurRequest(
        @NotBlank @Email @Size(max = 254) String email
) {
}
