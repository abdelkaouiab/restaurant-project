package com.restaurant.management.dto;
import jakarta.validation.constraints.*;
public record FournisseurRequest(@NotBlank @Size(max=150) String nom,@Email @Size(max=254) String email,@Pattern(regexp="^[+0-9 ().-]{0,30}$") String telephone,@Size(max=500) String adresse){}
