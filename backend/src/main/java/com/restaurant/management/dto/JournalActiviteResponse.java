package com.restaurant.management.dto;
import com.restaurant.management.enums.ResultatActivite; import java.time.Instant; import java.util.UUID;
public record JournalActiviteResponse(Long id,Long utilisateurId,String utilisateurNom,String action,String typeRessource,String ressourceId,ResultatActivite resultat,UUID correlationId,Instant creeLe) {}
