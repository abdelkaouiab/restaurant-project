package com.restaurant.management.repository;

import com.restaurant.management.entity.MouvementStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface MouvementStockRepository
        extends JpaRepository<MouvementStock, Long>, JpaSpecificationExecutor<MouvementStock> {

    Optional<MouvementStock> findByCleIdempotence(UUID cle);

    boolean existsByCommandeIdAndIngredientId(Long commandeId, Long ingredientId);

    @Override
    @EntityGraph(attributePaths = "ingredient")
    Page<MouvementStock> findAll(Specification<MouvementStock> specification, Pageable pageable);
}
