package com.restaurant.management.controller;

import com.restaurant.management.dto.CreerUtilisateurRequest;
import com.restaurant.management.dto.ModifierRolesUtilisateurRequest;
import com.restaurant.management.dto.ModifierStatutUtilisateurRequest;
import com.restaurant.management.dto.ModifierUtilisateurRequest;
import com.restaurant.management.dto.PageResponse;
import com.restaurant.management.dto.ReinitialiserMotDePasseRequest;
import com.restaurant.management.dto.UtilisateurAdministrationResponse;
import com.restaurant.management.service.UtilisateurAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/utilisateurs")
public class UtilisateurAdministrationController {

    private final UtilisateurAdministrationService service;

    public UtilisateurAdministrationController(UtilisateurAdministrationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<UtilisateurAdministrationResponse> creer(
            @Valid @RequestBody CreerUtilisateurRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(request));
    }

    @PutMapping("/{id}")
    public UtilisateurAdministrationResponse modifier(
            @PathVariable Long id,
            @Valid @RequestBody ModifierUtilisateurRequest request
    ) {
        return service.modifier(id, request);
    }

    @PutMapping("/{id}/roles")
    public UtilisateurAdministrationResponse modifierRoles(
            @PathVariable Long id,
            @Valid @RequestBody ModifierRolesUtilisateurRequest request
    ) {
        return service.modifierRoles(id, request);
    }

    @PatchMapping("/{id}/statut")
    public UtilisateurAdministrationResponse modifierStatut(
            @PathVariable Long id,
            @Valid @RequestBody ModifierStatutUtilisateurRequest request
    ) {
        return service.modifierStatut(id, request);
    }

    @PostMapping("/{id}/mot-de-passe/reinitialisation")
    public ResponseEntity<Void> reinitialiserMotDePasse(
            @PathVariable Long id,
            @Valid @RequestBody ReinitialiserMotDePasseRequest request
    ) {
        service.reinitialiserMotDePasse(id, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public PageResponse<UtilisateurAdministrationResponse> rechercher(
            @RequestParam(required = false) @Size(max = 100) String recherche,
            @RequestParam(required = false) Boolean actif,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int taille,
            @RequestParam(defaultValue = "email") String tri,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        return service.rechercher(recherche, actif, page, taille, tri, direction);
    }
}
