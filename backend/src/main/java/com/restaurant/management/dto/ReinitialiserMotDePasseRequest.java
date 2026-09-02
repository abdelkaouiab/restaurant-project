package com.restaurant.management.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReinitialiserMotDePasseRequest(
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank @Size(min = 12, max = 128) String nouveauMotDePasse
) {
}
