package com.restaurant.management.repository;
import com.restaurant.management.entity.Ingredient; import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*;
public interface IngredientRepository extends JpaRepository<Ingredient,Long> {
 boolean existsByNomIgnoreCase(String nom);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from Ingredient i where i.id=:id") Optional<Ingredient> findByIdAvecVerrou(@Param("id") Long id);
 List<Ingredient> findAllByOrderByNomAsc();
}
