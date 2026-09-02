package com.restaurant.management.dto;

public record UtilisateurJournalResponse(
        Long id,
        String email,
        String nom,
        String prenom,
        boolean actif
) {
}
