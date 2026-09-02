package com.restaurant.management.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException() {
        super("Ressource introuvable.");
    }
}
