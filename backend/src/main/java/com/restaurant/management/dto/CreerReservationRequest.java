package com.restaurant.management.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record CreerReservationRequest(
        @NotNull @Positive Long tableId,
        @NotNull @Positive Long clientId,
        @Min(1) @Max(100) int nombrePersonnes,
        @NotNull @Future Instant debut,
        @Size(max = 500) String remarques
) {}
