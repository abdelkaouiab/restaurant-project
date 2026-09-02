package com.restaurant.management.controller;

import com.restaurant.management.dto.CommandeCuisineResponse;
import com.restaurant.management.service.CuisineCommandeService;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/cuisine/commandes")
public class CuisineCommandeController {
    private final CuisineCommandeService service;

    public CuisineCommandeController(CuisineCommandeService service) {
        this.service = service;
    }

    @GetMapping
    public List<CommandeCuisineResponse> lister() {
        return service.lister();
    }

    @PostMapping("/{numero}/demarrage")
    public CommandeCuisineResponse demarrer(
            @PathVariable @Pattern(regexp = "CMD-[A-Z0-9-]{1,46}") String numero
    ) {
        return service.demarrer(numero);
    }

    @PostMapping("/{numero}/prete")
    public CommandeCuisineResponse marquerPrete(
            @PathVariable @Pattern(regexp = "CMD-[A-Z0-9-]{1,46}") String numero
    ) {
        return service.marquerPrete(numero);
    }
}
