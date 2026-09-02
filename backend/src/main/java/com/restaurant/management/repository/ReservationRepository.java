package com.restaurant.management.repository;

import com.restaurant.management.entity.Reservation;
import com.restaurant.management.enums.StatutReservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {
    boolean existsByNumero(String numero);

    @EntityGraph(attributePaths = {"table", "client"})
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findAvecDetailsById(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"table", "client"})
    Page<Reservation> findAll(Specification<Reservation> specification, Pageable pageable);

    @Query("""
        select count(r) from Reservation r
        where r.table.id = :tableId and r.statut in :statuts
          and r.debut < :fin and r.fin > :debut
        """)
    long countConflits(@Param("tableId") Long tableId,
                       @Param("debut") Instant debut,
                       @Param("fin") Instant fin,
                       @Param("statuts") Collection<StatutReservation> statuts);

    @EntityGraph(attributePaths = {"table"})
    @Query("""
        select r from Reservation r
        where r.table.id in :tableIds and r.statut in :statuts
          and r.debut <= :instant and r.fin > :instant
        order by r.debut
        """)
    List<Reservation> findEnCoursParTableIds(
            @Param("tableIds") Collection<Long> tableIds,
            @Param("statuts") Collection<StatutReservation> statuts,
            @Param("instant") Instant instant
    );
}
