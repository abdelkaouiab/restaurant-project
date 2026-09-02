package com.restaurant.management.dto;

import java.time.Instant;

public record ClientResponse(
        Long id, String nom, String prenom, String email, String telephone, boolean actif,
        Instant creeLe, Instant modifieLe, long version
) {
}
