package com.restaurant.management.dto;

public record CsrfResponse(
        String headerName,
        String parameterName,
        String token
) {
}
