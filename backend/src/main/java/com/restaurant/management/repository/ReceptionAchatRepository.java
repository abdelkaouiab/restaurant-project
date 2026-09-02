package com.restaurant.management.repository;

import com.restaurant.management.entity.ReceptionAchat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceptionAchatRepository extends JpaRepository<ReceptionAchat, Long> {
    Optional<ReceptionAchat> findByCleIdempotence(UUID cle);

    @EntityGraph(attributePaths = {"lignes", "lignes.ligneAchat", "lignes.ligneAchat.ingredient"})
    List<ReceptionAchat> findDistinctByAchatIdOrderByCreeLeDescIdDesc(Long achatId);
}
