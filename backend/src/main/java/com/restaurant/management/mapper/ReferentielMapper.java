package com.restaurant.management.mapper;

import com.restaurant.management.dto.CategorieResponse;
import com.restaurant.management.dto.ClientResponse;
import com.restaurant.management.dto.EmployeResponse;
import com.restaurant.management.dto.ProduitResponse;
import com.restaurant.management.dto.TableRestaurantResponse;
import com.restaurant.management.entity.Categorie;
import com.restaurant.management.entity.Client;
import com.restaurant.management.entity.Employe;
import com.restaurant.management.entity.Produit;
import com.restaurant.management.entity.TableRestaurant;
import org.springframework.stereotype.Component;

@Component
public class ReferentielMapper {

    public EmployeResponse versResponse(Employe e) {
        return new EmployeResponse(e.getId(), e.getMatricule(), e.getNom(), e.getPrenom(), e.getTelephone(),
                e.getPoste(), e.getDateEmbauche(), e.isActif(), e.getCreeLe(), e.getModifieLe(), e.getVersion());
    }

    public ClientResponse versResponse(Client c) {
        return new ClientResponse(c.getId(), c.getNom(), c.getPrenom(), c.getEmail(), c.getTelephone(),
                c.isActif(), c.getCreeLe(), c.getModifieLe(), c.getVersion());
    }

    public TableRestaurantResponse versResponse(TableRestaurant t) {
        return new TableRestaurantResponse(t.getId(), t.getNumero(), t.getCapacite(), t.isActif(),
                t.getCreeLe(), t.getModifieLe(), t.getVersion());
    }

    public CategorieResponse versResponse(Categorie c) {
        return new CategorieResponse(c.getId(), c.getNom(), c.getDescription(), c.isActif(),
                c.getCreeLe(), c.getModifieLe(), c.getVersion());
    }

    public ProduitResponse versResponse(Produit p) {
        return new ProduitResponse(p.getId(), p.getNom(), p.getDescription(), p.getPrix(),
                versResponse(p.getCategorie()), p.isDisponible(), p.isActif(),
                p.possedeImage() ? "/api/produits/" + p.getId() + "/image" : null,
                p.getImageTypeMime(), p.getCreeLe(), p.getModifieLe(), p.getVersion());
    }
}
