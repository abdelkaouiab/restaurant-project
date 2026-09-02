package com.restaurant.management.repository;

import com.restaurant.management.entity.Facture;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FactureRepository extends JpaRepository<Facture, Long> {
    Optional<Facture> findByCommandeId(Long commandeId);
    boolean existsByNumero(String numero);

    @EntityGraph(attributePaths = "commande")
    Page<Facture> findAllBy(Pageable pageable);
}
