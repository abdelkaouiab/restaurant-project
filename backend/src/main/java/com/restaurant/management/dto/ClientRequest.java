package com.restaurant.management.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClientRequest(
        @NotBlank @Size(max = 100) String nom,
        @NotBlank @Size(max = 100) String prenom,
        @Email @Size(max = 254) String email,
        @Size(max = 30) @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,30}$") String telephone
) {
}
