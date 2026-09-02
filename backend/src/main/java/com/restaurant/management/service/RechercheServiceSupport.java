package com.restaurant.management.service;

import com.restaurant.management.exception.InvalidSortFieldException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

final class RechercheServiceSupport {

    private RechercheServiceSupport() {
    }

    static Pageable pageable(
            int page,
            int taille,
            String tri,
            String direction,
            Map<String, String> champs
    ) {
        String champJpa = champs.get(tri);
        if (champJpa == null) {
            throw new InvalidSortFieldException();
        }
        try {
            return PageRequest.of(page, taille, Sort.by(Sort.Direction.fromString(direction), champJpa));
        } catch (IllegalArgumentException exception) {
            throw new InvalidSortFieldException();
        }
    }

    static String terme(String recherche) {
        return recherche == null || recherche.isBlank() ? null : recherche.strip().toLowerCase();
    }
}
