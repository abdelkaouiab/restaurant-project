package com.restaurant.management.dto;

import java.time.Instant;

public record TableRestaurantResponse(
        Long id, int numero, int capacite, boolean actif,
        Instant creeLe, Instant modifieLe, long version
) {
}
