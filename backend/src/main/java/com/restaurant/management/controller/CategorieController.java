package com.restaurant.management.controller;

import com.restaurant.management.dto.CategorieRequest;
import com.restaurant.management.dto.CategorieResponse;
import com.restaurant.management.dto.ModifierActifRequest;
import com.restaurant.management.dto.PageResponse;
import com.restaurant.management.service.CategorieService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/categories")
public class CategorieController {
    private final CategorieService service;
    public CategorieController(CategorieService service) { this.service = service; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public CategorieResponse creer(@Valid @RequestBody CategorieRequest request) { return service.creer(request); }
    @PutMapping("/{id}")
    public CategorieResponse modifier(@PathVariable Long id, @Valid @RequestBody CategorieRequest request) {
        return service.modifier(id, request);
    }
    @PatchMapping("/{id}/statut")
    public CategorieResponse statut(@PathVariable Long id, @Valid @RequestBody ModifierActifRequest request) {
        return service.modifierActif(id, request);
    }
    @GetMapping("/{id}")
    public CategorieResponse obtenir(@PathVariable Long id) { return service.obtenir(id); }
    @GetMapping
    public PageResponse<CategorieResponse> rechercher(
            @RequestParam(required = false) @Size(max = 100) String recherche,
            @RequestParam(required = false) Boolean actif,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int taille,
            @RequestParam(defaultValue = "nom") String tri,
            @RequestParam(defaultValue = "asc") String direction
    ) { return service.rechercher(recherche, actif, page, taille, tri, direction); }
}
