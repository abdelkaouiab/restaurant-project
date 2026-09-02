package com.restaurant.management.dto;
import jakarta.validation.constraints.*; import java.math.BigDecimal;
public record LigneReceptionAchatRequest(@NotNull @Positive Long ligneAchatId,@NotNull @DecimalMin(value="0.001") @Digits(integer=11,fraction=3) BigDecimal quantite){}
