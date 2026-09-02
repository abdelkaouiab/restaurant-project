package com.restaurant.management.dto;

import com.restaurant.management.enums.NomRole;

import java.time.Instant;
import java.util.Set;

public record UtilisateurAdministrationResponse(
        Long id,
        String email,
        boolean actif,
        EmployeUtilisateurResponse employe,
        Set<NomRole> roles,
        Instant creeLe,
        Instant modifieLe,
        long version
) {
}
