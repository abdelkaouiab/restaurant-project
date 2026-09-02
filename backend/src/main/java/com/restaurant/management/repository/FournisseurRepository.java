package com.restaurant.management.repository;
import com.restaurant.management.entity.Fournisseur; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface FournisseurRepository extends JpaRepository<Fournisseur,Long>,JpaSpecificationExecutor<Fournisseur>{boolean existsByNomIgnoreCase(String nom); Optional<Fournisseur> findByIdAndActifTrue(Long id);}
