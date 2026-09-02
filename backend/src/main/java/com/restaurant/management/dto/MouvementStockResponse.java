package com.restaurant.management.dto;
import com.restaurant.management.enums.TypeMouvement; import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record MouvementStockResponse(Long id,Long ingredientId,TypeMouvement type,BigDecimal quantite,BigDecimal variation,BigDecimal quantiteApres,String motif,UUID cleIdempotence,Instant creeLe){}
