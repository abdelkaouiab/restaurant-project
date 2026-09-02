package com.restaurant.management.repository;
import com.restaurant.management.entity.Achat; import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*;
public interface AchatRepository extends JpaRepository<Achat,Long>,JpaSpecificationExecutor<Achat>{@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select distinct a from Achat a left join fetch a.lignes where a.id=:id") Optional<Achat> findByIdAvecVerrou(@Param("id") Long id);}
