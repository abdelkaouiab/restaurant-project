package com.restaurant.management.mapper;

import com.restaurant.management.dto.CommandeAssocieeTableResponse;
import com.restaurant.management.dto.ReservationEnCoursTableResponse;
import com.restaurant.management.dto.TableRestaurantOperationnelleResponse;
import com.restaurant.management.entity.Commande;
import com.restaurant.management.entity.Reservation;
import com.restaurant.management.entity.TableRestaurant;
import com.restaurant.management.enums.StatutReservation;
import com.restaurant.management.enums.StatutTable;
import org.springframework.stereotype.Component;

@Component
public class TableRestaurantOperationnelleMapper {

    public TableRestaurantOperationnelleResponse versResponse(
            TableRestaurant table,
            Commande commande,
            Reservation reservation
    ) {
        StatutTable statut = determinerStatut(table, commande, reservation);
        return new TableRestaurantOperationnelleResponse(
                table.getId(),
                table.getNumero(),
                table.getCapacite(),
                table.isActif(),
                statut,
                statut == StatutTable.LIBRE,
                commande == null ? null : new CommandeAssocieeTableResponse(
                        commande.getId(), commande.getNumero(), commande.getStatut()
                ),
                reservation == null ? null : new ReservationEnCoursTableResponse(
                        reservation.getId(), reservation.getNumero(), reservation.getStatut(),
                        reservation.getNombrePersonnes(), reservation.getDebut(), reservation.getFin()
                ),
                table.getCreeLe(),
                table.getModifieLe(),
                table.getVersion()
        );
    }

    private StatutTable determinerStatut(
            TableRestaurant table,
            Commande commande,
            Reservation reservation
    ) {
        if (!table.isActif()) return StatutTable.HORS_SERVICE;
        if (commande != null
                || (reservation != null && reservation.getStatut() == StatutReservation.ARRIVEE)) {
            return StatutTable.OCCUPEE;
        }
        if (reservation != null) return StatutTable.RESERVEE;
        return StatutTable.LIBRE;
    }
}
