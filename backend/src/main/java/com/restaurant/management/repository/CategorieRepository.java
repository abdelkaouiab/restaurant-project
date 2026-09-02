package com.restaurant.management.repository;

import com.restaurant.management.entity.Categorie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CategorieRepository extends JpaRepository<Categorie, Long>, JpaSpecificationExecutor<Categorie> {

    boolean existsByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);
}
