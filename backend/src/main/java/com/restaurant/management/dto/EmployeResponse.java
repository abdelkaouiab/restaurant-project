package com.restaurant.management.dto;

import java.time.Instant;
import java.time.LocalDate;

public record EmployeResponse(
        Long id, String matricule, String nom, String prenom, String telephone,
        String poste, LocalDate dateEmbauche, boolean actif,
        Instant creeLe, Instant modifieLe, long version
) {
}
