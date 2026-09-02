package com.restaurant.management.dto;
import java.math.BigDecimal; import java.time.LocalDate; import java.util.List;
public record TableauBordFinancierResponse(LocalDate debut,LocalDate fin,BigDecimal chiffreAffaires,List<IndicateurValeurResponse> paiementsParMode,BigDecimal depensesAchat) {}
