package com.restaurant.management.dto;

import com.restaurant.management.enums.NomRole;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record ModifierRolesUtilisateurRequest(
        @NotEmpty Set<NomRole> roles
) {
}
