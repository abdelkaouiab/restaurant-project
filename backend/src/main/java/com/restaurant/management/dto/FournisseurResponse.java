package com.restaurant.management.dto;
public record FournisseurResponse(Long id,String nom,String email,String telephone,String adresse,boolean actif,long version){}
