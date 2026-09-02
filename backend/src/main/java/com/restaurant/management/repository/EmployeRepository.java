package com.restaurant.management.repository;

import com.restaurant.management.entity.Employe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface EmployeRepository extends JpaRepository<Employe, Long>, JpaSpecificationExecutor<Employe> {

    Optional<Employe> findByMatricule(String matricule);

    boolean existsByMatricule(String matricule);

    boolean existsByMatriculeAndIdNot(String matricule, Long id);
}
