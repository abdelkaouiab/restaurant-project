package com.restaurant.management.exception;

public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException() {
        super("Échec de l'authentification");
    }
}
