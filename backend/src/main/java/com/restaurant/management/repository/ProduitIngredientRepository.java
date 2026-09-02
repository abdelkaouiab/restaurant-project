package com.restaurant.management.repository;
import com.restaurant.management.entity.ProduitIngredient; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*;
public interface ProduitIngredientRepository extends JpaRepository<ProduitIngredient,Long> {
 @EntityGraph(attributePaths={"ingredient","produit"}) List<ProduitIngredient> findByProduitId(Long produitId);
 Optional<ProduitIngredient> findByProduitIdAndIngredientId(Long produitId,Long ingredientId);
 @Query("select pi from ProduitIngredient pi join fetch pi.ingredient where pi.produit.id in :ids") List<ProduitIngredient> findRecettes(@Param("ids") Collection<Long> ids);
}
