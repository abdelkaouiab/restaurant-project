package com.restaurant.management.repository;

import com.restaurant.management.entity.Produit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProduitRepository extends JpaRepository<Produit, Long>, JpaSpecificationExecutor<Produit> {

    boolean existsByCategorieIdAndActifTrue(Long categorieId);
}
