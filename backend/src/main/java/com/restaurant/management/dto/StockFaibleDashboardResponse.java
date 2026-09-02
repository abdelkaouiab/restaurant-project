package com.restaurant.management.dto;
import com.restaurant.management.enums.UniteMesure; import java.math.BigDecimal;
public record StockFaibleDashboardResponse(Long ingredientId,String ingredient,UniteMesure unite,BigDecimal quantiteDisponible,BigDecimal seuilAlerte) {}
