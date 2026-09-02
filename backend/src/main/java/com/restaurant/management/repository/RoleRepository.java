package com.restaurant.management.repository;

import com.restaurant.management.entity.Role;
import com.restaurant.management.enums.NomRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findWithPermissionsByNom(NomRole nom);

    Optional<Role> findByNom(NomRole nom);

    List<Role> findAllByNomIn(Collection<NomRole> noms);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.nom = :nom")
    Optional<Role> findByNomForUpdate(@Param("nom") NomRole nom);
}
