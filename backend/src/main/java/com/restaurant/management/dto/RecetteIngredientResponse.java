package com.restaurant.management.dto;
import com.restaurant.management.enums.UniteMesure; import java.math.BigDecimal;
public record RecetteIngredientResponse(Long ingredientId,String ingredient,UniteMesure unite,BigDecimal quantite,long version){}
