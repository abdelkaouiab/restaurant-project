package com.restaurant.management.controller;
import com.restaurant.management.dto.*; import com.restaurant.management.enums.StatutAchat; import com.restaurant.management.service.AchatService; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.http.HttpStatus; import org.springframework.validation.annotation.Validated; import org.springframework.web.bind.annotation.*; import java.util.List; import java.util.UUID;
@Validated @RestController @RequestMapping("/api/achats")
public class AchatController {
 private final AchatService service; public AchatController(AchatService s){service=s;}
 @PostMapping("/fournisseurs") @ResponseStatus(HttpStatus.CREATED) public FournisseurResponse creerFournisseur(@Valid @RequestBody FournisseurRequest r){return service.creerFournisseur(r);}
 @PutMapping("/fournisseurs/{id}") public FournisseurResponse modifierFournisseur(@PathVariable @Positive Long id,@Valid @RequestBody FournisseurRequest r){return service.modifierFournisseur(id,r);}
 @PatchMapping("/fournisseurs/{id}/statut") public FournisseurResponse modifierStatutFournisseur(@PathVariable @Positive Long id,@Valid @RequestBody ModifierStatutFournisseurRequest r){return service.modifierStatutFournisseur(id,r);}
 @GetMapping("/fournisseurs") public PageResponse<FournisseurResponse> fournisseurs(@RequestParam(required=false) @Size(max=100) String recherche,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int taille,@RequestParam(defaultValue="nom") String tri,@RequestParam(defaultValue="asc") String direction){return service.fournisseurs(recherche,page,taille,tri,direction);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public AchatResponse creer(@Valid @RequestBody CreerAchatRequest r){return service.creer(r);}
 @PostMapping("/{id}/lignes") public AchatResponse ajouterLigne(@PathVariable @Positive Long id,@Valid @RequestBody AjouterLigneAchatRequest r){return service.ajouterLigne(id,r);}
 @PostMapping("/{id}/commande") public AchatResponse commander(@PathVariable @Positive Long id){return service.commander(id);}
 @PostMapping("/{id}/receptions") public AchatResponse recevoir(@PathVariable @Positive Long id,@RequestHeader("Idempotency-Key") UUID cle,@Valid @RequestBody ReceptionAchatRequest r){return service.recevoir(id,cle,r);}
 @GetMapping("/{id}/receptions") public List<ReceptionAchatResponse> receptions(@PathVariable @Positive Long id){return service.receptions(id);}
 @PostMapping("/{id}/annulation") public AchatResponse annuler(@PathVariable @Positive Long id){return service.annuler(id);}
 @GetMapping("/{id}") public AchatResponse obtenir(@PathVariable @Positive Long id){return service.obtenir(id);}
 @GetMapping public PageResponse<AchatResponse> rechercher(@RequestParam(required=false) @Size(max=100) String recherche,@RequestParam(required=false) StatutAchat statut,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int taille,@RequestParam(defaultValue="creeLe") String tri,@RequestParam(defaultValue="desc") String direction){return service.rechercher(recherche,statut,page,taille,tri,direction);}
}
