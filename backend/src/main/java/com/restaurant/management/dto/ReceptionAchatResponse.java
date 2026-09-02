package com.restaurant.management.dto;

import java.time.Instant;
import java.util.List;

public record ReceptionAchatResponse(
        Long id,
        Instant creeLe,
        List<LigneReceptionAchatResponse> lignes
) {
}
