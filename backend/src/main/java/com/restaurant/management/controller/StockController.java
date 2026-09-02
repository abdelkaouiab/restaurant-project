package com.restaurant.management.controller;

import com.restaurant.management.dto.IngredientRequest;
import com.restaurant.management.dto.IngredientResponse;
import com.restaurant.management.dto.MouvementStockHistoriqueResponse;
import com.restaurant.management.dto.MouvementStockRequest;
import com.restaurant.management.dto.MouvementStockResponse;
import com.restaurant.management.dto.PageResponse;
import com.restaurant.management.dto.RecetteIngredientRequest;
import com.restaurant.management.dto.RecetteIngredientResponse;
import com.restaurant.management.enums.TypeMouvement;
import com.restaurant.management.service.StockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/stock")
public class StockController {
    private final StockService service;

    public StockController(StockService service) {
        this.service = service;
    }

    @PostMapping("/ingredients")
    @ResponseStatus(HttpStatus.CREATED)
    public IngredientResponse creer(@Valid @RequestBody IngredientRequest request) {
        return service.creerIngredient(request);
    }

    @GetMapping("/ingredients")
    public List<IngredientResponse> lister() {
        return service.listerIngredients();
    }

    @PutMapping("/ingredients/{id}")
    public IngredientResponse modifier(@PathVariable Long id, @Valid @RequestBody IngredientRequest request) {
        return service.modifierIngredient(id, request);
    }

    @PutMapping("/produits/{produitId}/recette")
    public RecetteIngredientResponse recette(
            @PathVariable Long produitId,
            @Valid @RequestBody RecetteIngredientRequest request
    ) {
        return service.definirRecette(produitId, request);
    }

    @GetMapping("/produits/{produitId}/recette")
    public List<RecetteIngredientResponse> recette(@PathVariable Long produitId) {
        return service.recette(produitId);
    }

    @DeleteMapping("/produits/{produitId}/recette/{ingredientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerIngredientRecette(
            @PathVariable Long produitId,
            @PathVariable Long ingredientId,
            @RequestParam @Min(0) long version
    ) {
        service.supprimerIngredientRecette(produitId, ingredientId, version);
    }

    @PostMapping("/mouvements")
    public MouvementStockResponse mouvement(
            @RequestHeader("Idempotency-Key") UUID cle,
            @Valid @RequestBody MouvementStockRequest request
    ) {
        return service.mouvement(cle, request);
    }

    @GetMapping("/mouvements")
    public PageResponse<MouvementStockHistoriqueResponse> mouvements(
            @RequestParam(required = false) Long ingredientId,
            @RequestParam(required = false) TypeMouvement type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int taille
    ) {
        return service.listerMouvements(ingredientId, type, page, taille);
    }
}
