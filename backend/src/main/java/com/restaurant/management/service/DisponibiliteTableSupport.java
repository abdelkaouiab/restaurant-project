package com.restaurant.management.service;

import com.restaurant.management.enums.StatutCommande;
import com.restaurant.management.enums.StatutReservation;

import java.util.List;

final class DisponibiliteTableSupport {

    static final List<StatutCommande> COMMANDES_OCCUPANTES = List.of(
            StatutCommande.BROUILLON,
            StatutCommande.CONFIRMEE,
            StatutCommande.EN_PREPARATION,
            StatutCommande.PRETE
    );

    static final List<StatutReservation> RESERVATIONS_OCCUPANTES = List.of(
            StatutReservation.EN_ATTENTE,
            StatutReservation.CONFIRMEE,
            StatutReservation.ARRIVEE
    );

    private DisponibiliteTableSupport() {
    }
}
