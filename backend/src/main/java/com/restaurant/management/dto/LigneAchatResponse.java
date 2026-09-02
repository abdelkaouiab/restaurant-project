package com.restaurant.management.dto;
import com.restaurant.management.enums.UniteMesure; import java.math.BigDecimal;
public record LigneAchatResponse(Long id,Long ingredientId,String ingredient,UniteMesure unite,BigDecimal quantiteCommandee,BigDecimal quantiteRecue,BigDecimal quantiteRestante,BigDecimal prixUnitaire,BigDecimal sousTotal,long version){}
