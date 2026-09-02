package com.restaurant.management.service;
import com.restaurant.management.exception.BusinessConflictException; import java.time.*;
import java.time.temporal.ChronoUnit;
record PeriodeTableauBord(LocalDate debut,LocalDate fin,Instant debutInstant,Instant finExclusive){
 static PeriodeTableauBord creer(LocalDate debut,LocalDate fin){LocalDate d=debut==null?LocalDate.now(ZoneOffset.UTC).minusDays(29):debut;LocalDate f=fin==null?LocalDate.now(ZoneOffset.UTC):fin;if(f.isBefore(d))throw new BusinessConflictException("La date de fin doit être postérieure à la date de début.");if(ChronoUnit.DAYS.between(d,f)>365)throw new BusinessConflictException("La période ne peut pas dépasser 366 jours.");return new PeriodeTableauBord(d,f,d.atStartOfDay(ZoneOffset.UTC).toInstant(),f.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());}
}
