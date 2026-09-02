package com.restaurant.management.dto;
import jakarta.validation.constraints.*; import java.math.BigDecimal;
public record RecetteIngredientRequest(@NotNull @Positive Long ingredientId,@NotNull @DecimalMin("0.001") @Digits(integer=11,fraction=3) BigDecimal quantite){}
