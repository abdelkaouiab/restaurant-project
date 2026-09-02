package com.restaurant.management.dto;
import java.time.LocalDate; import java.util.List;
public record TableauBordOperationsResponse(LocalDate debut,LocalDate fin,long nombreCommandes,long nombreTablesDisponibles,List<IndicateurValeurResponse> commandesParEtat,List<ProduitVenduResponse> produitsLesPlusVendus,List<IndicateurValeurResponse> reservations,List<StockFaibleDashboardResponse> stocksFaibles) {}
