package com.restaurant.management.repository;

import com.restaurant.management.entity.Paiement;
import com.restaurant.management.enums.StatutPaiement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PaiementRepository extends JpaRepository<Paiement, Long> {
    Optional<Paiement> findByCleIdempotence(UUID cleIdempotence);

    @Query("""
            select coalesce(sum(p.montant), 0)
            from Paiement p
            where p.commande.id = :commandeId and p.statut = :statut
            """)
    BigDecimal sommeParCommandeEtStatut(
            @Param("commandeId") Long commandeId,
            @Param("statut") StatutPaiement statut
    );

    @EntityGraph(attributePaths = "commande")
    Page<Paiement> findByStatutAndCreeLeGreaterThanEqualAndCreeLeLessThan(
            StatutPaiement statut,
            Instant debut,
            Instant finExclusive,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "commande")
    Page<Paiement> findByCommandeIdAndStatut(Long commandeId, StatutPaiement statut, Pageable pageable);
}
