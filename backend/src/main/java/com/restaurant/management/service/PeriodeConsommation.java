package com.restaurant.management.service;

import com.restaurant.management.exception.BusinessConflictException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

record PeriodeConsommation(
        LocalDate debut,
        LocalDate fin,
        Instant debutInstant,
        Instant finExclusive
) {
    static PeriodeConsommation creer(LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null) {
            throw new BusinessConflictException("Les dates de début et de fin sont obligatoires.");
        }
        if (fin.isBefore(debut)) {
            throw new BusinessConflictException("La date de fin doit être postérieure à la date de début.");
        }
        if (ChronoUnit.DAYS.between(debut, fin) > 365) {
            throw new BusinessConflictException("La période ne peut pas dépasser 366 jours.");
        }
        return new PeriodeConsommation(
                debut,
                fin,
                debut.atStartOfDay(ZoneOffset.UTC).toInstant(),
                fin.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        );
    }
}
