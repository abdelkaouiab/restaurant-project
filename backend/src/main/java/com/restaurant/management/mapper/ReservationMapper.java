package com.restaurant.management.mapper;

import com.restaurant.management.dto.ReservationResponse;
import com.restaurant.management.entity.Commande;
import com.restaurant.management.entity.Reservation;
import com.restaurant.management.repository.CommandeRepository;
import org.springframework.stereotype.Component;

@Component
public class ReservationMapper {
    private final CommandeRepository commandeRepository;
    public ReservationMapper(CommandeRepository commandeRepository) { this.commandeRepository = commandeRepository; }

    public ReservationResponse versResponse(Reservation reservation) {
        String commande = commandeRepository.findByReservationId(reservation.getId())
                .map(Commande::getNumero).orElse(null);
        return new ReservationResponse(
                reservation.getId(), reservation.getNumero(), reservation.getTable().getId(),
                reservation.getTable().getNumero(), reservation.getClient().getId(),
                reservation.getNombrePersonnes(), reservation.getDebut(), reservation.getFin(),
                reservation.getStatut(), reservation.getRemarques(), commande, reservation.getVersion()
        );
    }
}
