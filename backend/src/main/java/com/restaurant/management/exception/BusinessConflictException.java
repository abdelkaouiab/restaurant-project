package com.restaurant.management.exception;

public class BusinessConflictException extends RuntimeException {

    public BusinessConflictException(String message) {
        super(message);
    }
}
