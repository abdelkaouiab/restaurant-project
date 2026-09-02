package com.restaurant.management.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

public record TableRestaurantRequest(
        @Positive @Max(10000) int numero,
        @Positive @Max(1000) int capacite
) {
}
