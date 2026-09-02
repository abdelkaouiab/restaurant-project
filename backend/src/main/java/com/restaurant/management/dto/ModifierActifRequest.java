package com.restaurant.management.dto;

import jakarta.validation.constraints.NotNull;

public record ModifierActifRequest(@NotNull Boolean actif) {
}
