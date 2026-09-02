package com.restaurant.management.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.restaurant.management.enums.NomRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreerUtilisateurRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank @Size(min = 12, max = 128) String motDePasse,
        @NotNull @Positive Long employeId,
        @NotEmpty Set<NomRole> roles
) {
}
