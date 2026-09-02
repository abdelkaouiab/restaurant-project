package com.restaurant.management.repository;

import com.restaurant.management.entity.TableRestaurant;
import com.restaurant.management.enums.StatutCommande;
import com.restaurant.management.enums.StatutReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.Collection;
import java.time.Instant;

public interface TableRestaurantRepository
        extends JpaRepository<TableRestaurant, Long>, JpaSpecificationExecutor<TableRestaurant> {

    boolean existsByNumero(int numero);

    boolean existsByNumeroAndIdNot(int numero, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TableRestaurant t where t.id = :id")
    Optional<TableRestaurant> findByIdAvecVerrou(@Param("id") Long id);

    @Query("""
            select count(t) from TableRestaurant t
            where t.actif = true
              and not exists (
                  select c.id from Commande c
                  where c.table = t and c.statut in :statutsCommandes
              )
              and not exists (
                  select r.id from Reservation r
                  where r.table = t and r.statut in :statutsReservations
                    and r.debut <= :instant and r.fin > :instant
              )
            """)
    long countDisponibles(
            @Param("statutsCommandes") Collection<StatutCommande> statutsCommandes,
            @Param("statutsReservations") Collection<StatutReservation> statutsReservations,
            @Param("instant") Instant instant
    );
}
