package com.restaurant.management.dto;
import com.restaurant.management.enums.StatutAchat; import java.math.BigDecimal; import java.time.Instant; import java.util.List;
public record AchatResponse(Long id,String numero,FournisseurResponse fournisseur,StatutAchat statut,BigDecimal montantTotal,List<LigneAchatResponse> lignes,Instant commandeLe,Instant recueLe,Instant annuleLe,Instant creeLe,Instant modifieLe,long version){}
