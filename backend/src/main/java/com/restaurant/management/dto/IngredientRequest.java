package com.restaurant.management.dto;
import com.restaurant.management.enums.UniteMesure; import jakarta.validation.constraints.*; import java.math.BigDecimal;
public record IngredientRequest(@NotBlank @Size(max=150) String nom,@NotNull UniteMesure unite,@NotNull @DecimalMin("0.000") @Digits(integer=11,fraction=3) BigDecimal seuilAlerte){}
