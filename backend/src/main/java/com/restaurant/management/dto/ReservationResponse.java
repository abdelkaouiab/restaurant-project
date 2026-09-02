package com.restaurant.management.dto;

import com.restaurant.management.enums.StatutReservation;
import java.time.Instant;

public record ReservationResponse(
        Long id, String numero, Long tableId, int numeroTable, Long clientId,
        int nombrePersonnes, Instant debut, Instant fin, StatutReservation statut,
        String remarques, String numeroCommande, long version
) {}
