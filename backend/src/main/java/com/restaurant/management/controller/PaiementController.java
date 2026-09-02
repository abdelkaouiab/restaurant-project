package com.restaurant.management.controller;

import com.restaurant.management.dto.CommandeAPayerResponse;
import com.restaurant.management.dto.EnregistrerPaiementRequest;
import com.restaurant.management.dto.FactureResponse;
import com.restaurant.management.dto.PageResponse;
import com.restaurant.management.dto.PaiementHistoriqueResponse;
import com.restaurant.management.dto.PaiementResponse;
import com.restaurant.management.service.PaiementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/caisse")
public class PaiementController {
    private final PaiementService service;

    public PaiementController(PaiementService service) {
        this.service = service;
    }

    @GetMapping("/commandes-a-payer")
    public List<CommandeAPayerResponse> listerCommandesAPayer() {
        return service.listerCommandesAPayer();
    }

    @GetMapping("/paiements")
    public PageResponse<PaiementHistoriqueResponse> listerPaiementsDuJour(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "5") @Min(1) @Max(50) int taille
    ) {
        return service.listerPaiementsDuJour(date, page, taille);
    }

    @GetMapping("/factures")
    public PageResponse<FactureResponse> listerFacturesRecentes(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "5") @Min(1) @Max(50) int taille
    ) {
        return service.listerFacturesRecentes(page, taille);
    }

    @PostMapping("/commandes/{numero}/paiements")
    public PaiementResponse enregistrer(
            @PathVariable @Pattern(regexp = "CMD-[A-Z0-9-]{1,46}") String numero,
            @RequestHeader("Idempotency-Key") UUID cleIdempotence,
            @Valid @RequestBody EnregistrerPaiementRequest request
    ) {
        return service.enregistrer(numero, cleIdempotence, request);
    }

    @GetMapping("/commandes/{numero}/paiements")
    public PageResponse<PaiementHistoriqueResponse> listerPaiementsCommande(
            @PathVariable @Pattern(regexp = "CMD-[A-Z0-9-]{1,46}") String numero,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int taille
    ) {
        return service.listerPaiementsCommande(numero, page, taille);
    }

    @GetMapping("/commandes/{numero}/facture")
    public FactureResponse obtenirFacture(
            @PathVariable @Pattern(regexp = "CMD-[A-Z0-9-]{1,46}") String numero
    ) {
        return service.obtenirFacture(numero);
    }

    @GetMapping(value = "/commandes/{numero}/facture.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> obtenirFacturePdf(
            @PathVariable @Pattern(regexp = "CMD-[A-Z0-9-]{1,46}") String numero
    ) {
        FactureResponse facture = service.obtenirFacture(numero);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(facture.numero() + ".pdf")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(service.obtenirFacturePdf(numero));
    }
}
