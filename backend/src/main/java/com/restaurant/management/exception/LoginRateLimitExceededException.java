package com.restaurant.management.exception;

public class LoginRateLimitExceededException extends RuntimeException {

    public LoginRateLimitExceededException() {
        super("Trop de tentatives de connexion");
    }
}
