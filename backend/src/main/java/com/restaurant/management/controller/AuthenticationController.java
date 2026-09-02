package com.restaurant.management.controller;

import com.restaurant.management.dto.ConnexionRequest;
import com.restaurant.management.dto.CsrfResponse;
import com.restaurant.management.dto.UtilisateurAuthentifieResponse;
import com.restaurant.management.security.RestaurantUserPrincipal;
import com.restaurant.management.security.SessionAuthenticationService;
import com.restaurant.management.service.UtilisateurAuthentifieService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final SessionAuthenticationService sessionAuthenticationService;
    private final UtilisateurAuthentifieService utilisateurAuthentifieService;

    public AuthenticationController(
            SessionAuthenticationService sessionAuthenticationService,
            UtilisateurAuthentifieService utilisateurAuthentifieService
    ) {
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.utilisateurAuthentifieService = utilisateurAuthentifieService;
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(
                        csrfToken.getHeaderName(),
                        csrfToken.getParameterName(),
                        csrfToken.getToken()
                ));
    }

    @PostMapping("/login")
    public ResponseEntity<UtilisateurAuthentifieResponse> login(
            @Valid @RequestBody ConnexionRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(sessionAuthenticationService.login(requestBody, request, response));
    }

    @GetMapping("/me")
    public ResponseEntity<UtilisateurAuthentifieResponse> me(
            @AuthenticationPrincipal RestaurantUserPrincipal principal
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(utilisateurAuthentifieService.getById(principal.getUtilisateurId()));
    }
}
