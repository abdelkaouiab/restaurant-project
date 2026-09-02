package com.restaurant.management.repository;

import com.restaurant.management.entity.Utilisateur;
import com.restaurant.management.enums.NomRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByEmployeId(Long employeId);

    @EntityGraph(attributePaths = {"employe", "roles", "roles.permissions"})
    Optional<Utilisateur> findWithEmployeAndRolesById(Long id);

    @EntityGraph(attributePaths = {"employe", "roles", "roles.permissions"})
    Optional<Utilisateur> findWithEmployeAndRolesByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"employe", "roles"})
    Optional<Utilisateur> findWithEmployeAndRolesByEmployeId(Long employeId);

    @Query("""
            select distinct u from Utilisateur u
            join u.employe e
            where (:recherche = ''
                or lower(u.email) like lower(concat('%', :recherche, '%'))
                or lower(e.nom) like lower(concat('%', :recherche, '%'))
                or lower(e.prenom) like lower(concat('%', :recherche, '%'))
                or lower(e.matricule) like lower(concat('%', :recherche, '%')))
              and (:actif is null or u.actif = :actif)
            """)
    Page<Utilisateur> rechercher(
            @Param("recherche") String recherche,
            @Param("actif") Boolean actif,
            Pageable pageable
    );

    @Query("""
            select count(distinct u.id) from Utilisateur u
            join u.roles r
            where u.actif = true
              and u.employe.actif = true
              and r.actif = true
              and r.nom = :role
            """)
    long countAdministrateursActifs(@Param("role") NomRole role);
}
