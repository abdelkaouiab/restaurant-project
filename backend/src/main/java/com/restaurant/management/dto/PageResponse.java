package com.restaurant.management.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(
        List<T> contenu,
        int page,
        int taille,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> depuis(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
