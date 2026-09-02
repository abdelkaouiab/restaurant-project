package com.restaurant.management.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ModifierAssociationCommandeRequest(
        @NotNull @Positive Long tableId,
        @Positive Long clientId,
        @Null BigDecimal montantTotal,
        @Null BigDecimal total
) {
}
