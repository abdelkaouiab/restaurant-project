package com.restaurant.management.dto;

import com.restaurant.management.enums.StatutTable;

import java.time.Instant;

public record TableRestaurantOperationnelleResponse(
        Long id,
        int numero,
        int capacite,
        boolean actif,
        StatutTable statutOperationnel,
        boolean disponible,
        CommandeAssocieeTableResponse commandeAssociee,
        ReservationEnCoursTableResponse reservationEnCours,
        Instant creeLe,
        Instant modifieLe,
        long version
) {
}
