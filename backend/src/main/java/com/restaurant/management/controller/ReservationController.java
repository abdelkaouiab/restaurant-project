package com.restaurant.management.controller;

import com.restaurant.management.dto.CreerReservationRequest;
import com.restaurant.management.dto.ReservationResponse;
import com.restaurant.management.dto.PageResponse;
import com.restaurant.management.enums.StatutReservation;
import com.restaurant.management.service.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@Validated
@RequestMapping("/api/reservations")
public class ReservationController {
    private final ReservationService service;
    public ReservationController(ReservationService service) { this.service = service; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse creer(@Valid @RequestBody CreerReservationRequest request) { return service.creer(request); }
    @GetMapping public PageResponse<ReservationResponse> lister(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) StatutReservation statut,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int taille
    ) { return service.lister(date, statut, page, taille); }
    @PostMapping("/{id}/{action:confirmation|arrivee|terminaison|annulation|absence}")
    public ReservationResponse transition(@PathVariable Long id, @PathVariable String action) {
        return service.transition(id, action);
    }
    @PostMapping("/{id}/commande") @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse creerCommande(@PathVariable Long id) { return service.creerCommande(id); }
}
