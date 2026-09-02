package com.restaurant.management.controller;

import com.restaurant.management.dto.AjouterLigneCommandeRequest;
import com.restaurant.management.dto.CommandeResponse;
import com.restaurant.management.dto.CreerCommandeRequest;
import com.restaurant.management.dto.ModifierAssociationCommandeRequest;
import com.restaurant.management.dto.ModifierQuantiteLigneRequest;
import com.restaurant.management.dto.PageResponse;
import com.restaurant.management.enums.StatutCommande;
import com.restaurant.management.service.CommandeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/commandes")
public class CommandeController {

    private final CommandeService service;

    public CommandeController(CommandeService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommandeResponse creer(@Valid @RequestBody CreerCommandeRequest request) {
        return service.creer(request);
    }

    @PutMapping("/{id}/association")
    public CommandeResponse modifierAssociation(
            @PathVariable Long id,
            @Valid @RequestBody ModifierAssociationCommandeRequest request
    ) {
        return service.modifierAssociation(id, request);
    }

    @PostMapping("/{id}/lignes")
    public CommandeResponse ajouterLigne(
            @PathVariable Long id,
            @Valid @RequestBody AjouterLigneCommandeRequest request
    ) {
        return service.ajouterLigne(id, request);
    }

    @PutMapping("/{id}/lignes/{ligneId}")
    public CommandeResponse modifierQuantite(
            @PathVariable Long id,
            @PathVariable Long ligneId,
            @Valid @RequestBody ModifierQuantiteLigneRequest request
    ) {
        return service.modifierQuantite(id, ligneId, request);
    }

    @DeleteMapping("/{id}/lignes/{ligneId}")
    public CommandeResponse supprimerLigne(@PathVariable Long id, @PathVariable Long ligneId) {
        return service.supprimerLigne(id, ligneId);
    }

    @PostMapping("/{id}/confirmation")
    public CommandeResponse confirmer(@PathVariable Long id) {
        return service.confirmer(id);
    }

    @PostMapping("/{id}/annulation")
    public CommandeResponse annuler(@PathVariable Long id) {
        return service.annuler(id);
    }

    @GetMapping("/{id}")
    public CommandeResponse obtenir(@PathVariable Long id) {
        return service.obtenir(id);
    }

    @GetMapping
    public PageResponse<CommandeResponse> rechercher(
            @RequestParam(required = false) @Size(max = 100) String recherche,
            @RequestParam(required = false) StatutCommande statut,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int taille,
            @RequestParam(defaultValue = "creeLe") String tri,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return service.rechercher(recherche, statut, page, taille, tri, direction);
    }
}
