package com.restaurant.management.dto;

import java.util.Set;

public record UtilisateurAuthentifieResponse(
        Long id,
        String email,
        EmployeAuthentifieResponse employe,
        Set<String> roles,
        Set<String> permissions
) {
}
