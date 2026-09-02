package com.restaurant.management.service;

import com.restaurant.management.dto.ConsommationResponse;
import com.restaurant.management.repository.ConsommationRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class ConsommationService {

    private final ConsommationRepository repository;

    public ConsommationService(ConsommationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRATEUR','GERANT')")
    public ConsommationResponse consulter(LocalDate debut, LocalDate fin) {
        PeriodeConsommation periode = PeriodeConsommation.creer(debut, fin);
        return new ConsommationResponse(
                periode.debut(),
                periode.fin(),
                repository.produitsConsommes(periode.debutInstant(), periode.finExclusive()),
                repository.ingredientsUtilises(periode.debutInstant(), periode.finExclusive())
        );
    }
}
