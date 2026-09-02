package com.restaurant.management.repository;

import com.restaurant.management.entity.Commande;
import com.restaurant.management.enums.StatutCommande;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommandeRepository extends JpaRepository<Commande, Long>, JpaSpecificationExecutor<Commande> {

    boolean existsByNumero(String numero);
    boolean existsByReservationId(Long reservationId);
    @EntityGraph(attributePaths = {"table", "client", "reservation"})
    Optional<Commande> findByReservationId(Long reservationId);

    @EntityGraph(attributePaths = {
            "table", "client", "creePar", "lignes", "lignes.produit", "lignes.produit.categorie"
    })
    @Query("select c from Commande c where c.id = :id")
    Optional<Commande> findAvecDetailsById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"table", "lignes", "lignes.produit"})
    @Query("""
            select distinct c from Commande c
            where c.statut in :statutsActifs
               or (c.statut = :statutPret and c.preteLe >= :preteDepuis)
            order by c.confirmeeLe
            """)
    List<Commande> findPourCuisine(
            @Param("statutsActifs") Collection<StatutCommande> statutsActifs,
            @Param("statutPret") StatutCommande statutPret,
            @Param("preteDepuis") Instant preteDepuis
    );

    @EntityGraph(attributePaths = {"table", "lignes", "lignes.produit"})
    @Query("select c from Commande c where c.numero = :numero")
    Optional<Commande> findAvecDetailsByNumero(@Param("numero") String numero);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"table", "reservation"})
    @Query("select c from Commande c where c.numero = :numero")
    Optional<Commande> findPourPaiementAvecVerrou(@Param("numero") String numero);

    @EntityGraph(attributePaths = {"table"})
    @Query("""
            select c from Commande c
            where c.statut in :statuts
            order by c.confirmeeLe
            """)
    List<Commande> findCommandesAPayer(@Param("statuts") Collection<StatutCommande> statuts);

    @EntityGraph(attributePaths = {"table"})
    @Query("""
            select c from Commande c
            where c.table.id in :tableIds and c.statut in :statuts
            order by c.creeLe desc
            """)
    List<Commande> findActivesParTableIds(
            @Param("tableIds") Collection<Long> tableIds,
            @Param("statuts") Collection<StatutCommande> statuts
    );
}
