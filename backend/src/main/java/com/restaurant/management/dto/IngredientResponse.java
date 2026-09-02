package com.restaurant.management.dto;
import com.restaurant.management.enums.UniteMesure; import java.math.BigDecimal;
public record IngredientResponse(Long id,String nom,UniteMesure unite,BigDecimal quantiteDisponible,BigDecimal seuilAlerte,boolean stockFaible,boolean actif,long version){}
