package com.restaurant.management.dto;
import jakarta.validation.constraints.*; import java.math.BigDecimal;
public record AjouterLigneAchatRequest(@NotNull @Positive Long ingredientId,@NotNull @DecimalMin(value="0.001") @Digits(integer=11,fraction=3) BigDecimal quantite,@NotNull @DecimalMin(value="0.01") @Digits(integer=12,fraction=2) BigDecimal prixUnitaire){}
