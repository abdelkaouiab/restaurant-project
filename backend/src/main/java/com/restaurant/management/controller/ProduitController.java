package com.restaurant.management.controller;

import com.restaurant.management.dto.ModifierActifRequest;
import com.restaurant.management.dto.PageResponse;
import com.restaurant.management.dto.ProduitRequest;
import com.restaurant.management.dto.ProduitResponse;
import com.restaurant.management.service.ProduitImageStorageService;
import com.restaurant.management.service.ProduitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/produits")
public class ProduitController {
    private final ProduitService service;
    public ProduitController(ProduitService service) { this.service = service; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ProduitResponse creer(@Valid @RequestBody ProduitRequest request) { return service.creer(request); }
    @PutMapping("/{id}")
    public ProduitResponse modifier(@PathVariable Long id, @Valid @RequestBody ProduitRequest request) {
        return service.modifier(id, request);
    }
    @PatchMapping("/{id}/statut")
    public ProduitResponse statut(@PathVariable Long id, @Valid @RequestBody ModifierActifRequest request) {
        return service.modifierActif(id, request);
    }
    @GetMapping("/{id}")
    public ProduitResponse obtenir(@PathVariable Long id) { return service.obtenir(id); }
    @PutMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProduitResponse enregistrerImage(
            @PathVariable Long id,
            @RequestPart("fichier") MultipartFile fichier
    ) {
        return service.enregistrerImage(id, fichier);
    }
    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> obtenirImage(@PathVariable Long id) {
        ProduitImageStorageService.ImageLue image = service.obtenirImage(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.typeMime()))
                .contentLength(image.taille())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("produit-" + id).build().toString())
                .body(image.ressource());
    }
    @DeleteMapping("/{id}/image")
    public ProduitResponse supprimerImage(@PathVariable Long id) { return service.supprimerImage(id); }
    @GetMapping
    public PageResponse<ProduitResponse> rechercher(
            @RequestParam(required = false) @Size(max = 100) String recherche,
            @RequestParam(required = false) @Positive Long categorieId,
            @RequestParam(required = false) Boolean actif,
            @RequestParam(required = false) Boolean disponible,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int taille,
            @RequestParam(defaultValue = "nom") String tri,
            @RequestParam(defaultValue = "asc") String direction
    ) { return service.rechercher(recherche, categorieId, actif, disponible, page, taille, tri, direction); }
}
