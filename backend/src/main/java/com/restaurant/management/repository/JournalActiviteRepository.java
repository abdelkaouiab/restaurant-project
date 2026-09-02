package com.restaurant.management.repository;

import com.restaurant.management.entity.JournalActivite;
import com.restaurant.management.dto.UtilisateurJournalResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface JournalActiviteRepository extends JpaRepository<JournalActivite, Long>, JpaSpecificationExecutor<JournalActivite> {

    Page<JournalActivite> findByUtilisateurId(Long utilisateurId, Pageable pageable);

    Page<JournalActivite> findByCorrelationId(UUID correlationId, Pageable pageable);

    @Query("""
            select new com.restaurant.management.dto.UtilisateurJournalResponse(
                u.id, u.email, e.nom, e.prenom, u.actif
            )
            from Utilisateur u
            join u.employe e
            where exists (
                select j.id from JournalActivite j where j.utilisateur = u
            )
            order by e.nom, e.prenom, u.email, u.id
            """)
    List<UtilisateurJournalResponse> findUtilisateursDuJournal();
}
