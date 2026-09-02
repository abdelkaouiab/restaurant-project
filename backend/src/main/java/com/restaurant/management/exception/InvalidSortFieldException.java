package com.restaurant.management.exception;

public class InvalidSortFieldException extends RuntimeException {

    public InvalidSortFieldException() {
        super("Champ de tri non autorisé.");
    }
}
