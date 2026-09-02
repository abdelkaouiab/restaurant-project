package com.restaurant.management.mapper;

import com.restaurant.management.dto.CommandeCuisineResponse;
import com.restaurant.management.dto.ProduitCuisineResponse;
import com.restaurant.management.entity.Commande;
import org.springframework.stereotype.Component;

@Component
public class CuisineCommandeMapper {
    public CommandeCuisineResponse versResponse(Commande commande) {
        return new CommandeCuisineResponse(
                commande.getNumero(),
                commande.getTable().getNumero(),
                commande.getLignes().stream()
                        .map(ligne -> new ProduitCuisineResponse(
                                ligne.getProduit().getNom(),
                                ligne.getQuantite(),
                                ligne.getRemarque()
                        ))
                        .toList(),
                commande.getConfirmeeLe(),
                commande.getStatut()
        );
    }
}
