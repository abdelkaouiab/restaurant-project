package com.restaurant.management.dto;

import java.time.Instant;

public record CategorieResponse(
        Long id, String nom, String description, boolean actif,
        Instant creeLe, Instant modifieLe, long version
) {
}
