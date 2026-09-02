package com.restaurant.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record EmployeRequest(
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String matricule,
        @NotBlank @Size(max = 100) String nom,
        @NotBlank @Size(max = 100) String prenom,
        @Size(max = 30) @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,30}$") String telephone,
        @NotBlank @Size(max = 100) String poste,
        @NotNull @PastOrPresent LocalDate dateEmbauche
) {
}
