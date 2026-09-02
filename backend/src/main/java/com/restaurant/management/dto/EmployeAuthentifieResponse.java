package com.restaurant.management.dto;

public record EmployeAuthentifieResponse(
        Long id,
        String matricule,
        String nom,
        String prenom,
        String poste
) {
}
