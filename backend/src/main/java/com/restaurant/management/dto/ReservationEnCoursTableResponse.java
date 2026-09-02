package com.restaurant.management.dto;

import com.restaurant.management.enums.StatutReservation;

import java.time.Instant;

public record ReservationEnCoursTableResponse(
        Long id,
        String numero,
        StatutReservation statut,
        int nombrePersonnes,
        Instant debut,
        Instant fin
) {
}
