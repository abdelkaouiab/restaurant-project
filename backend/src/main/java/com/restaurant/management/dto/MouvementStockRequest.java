package com.restaurant.management.dto;
import com.restaurant.management.enums.TypeMouvement; import jakarta.validation.constraints.*; import java.math.BigDecimal;
public record MouvementStockRequest(@NotNull @Positive Long ingredientId,@NotNull TypeMouvement type,@NotNull @Digits(integer=11,fraction=3) BigDecimal quantite,@Size(max=500) String motif){}
