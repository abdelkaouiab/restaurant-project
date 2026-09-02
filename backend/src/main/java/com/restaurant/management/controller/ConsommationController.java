package com.restaurant.management.controller;

import com.restaurant.management.dto.ConsommationResponse;
import com.restaurant.management.service.ConsommationPdfService;
import com.restaurant.management.service.ConsommationService;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/consommations")
public class ConsommationController {

    private final ConsommationService consommationService;
    private final ConsommationPdfService pdfService;

    public ConsommationController(
            ConsommationService consommationService,
            ConsommationPdfService pdfService
    ) {
        this.consommationService = consommationService;
        this.pdfService = pdfService;
    }

    @GetMapping
    public ConsommationResponse consulter(
            @RequestParam(required = false) @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin
    ) {
        return consommationService.consulter(debut, fin);
    }

    @GetMapping(value = "/rapport.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> telechargerRapport(
            @RequestParam(required = false) @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin
    ) {
        String nomFichier = "rapport-consommation-%s-au-%s.pdf".formatted(debut, fin);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(nomFichier)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfService.generer(debut, fin));
    }
}
