package com.restaurant.management.repository;

import com.restaurant.management.entity.Permission;
import com.restaurant.management.enums.NomPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByNom(NomPermission nom);
}
