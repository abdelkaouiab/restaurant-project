package com.restaurant.management.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record FactureResponse(
        String numero,
        String numeroCommande,
        BigDecimal montantTotal,
        Instant emiseLe
) {
}
