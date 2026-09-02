package com.restaurant.management.security;

import com.restaurant.management.dto.ConnexionRequest;
import com.restaurant.management.dto.UtilisateurAuthentifieResponse;
import com.restaurant.management.exception.AuthenticationFailedException;
import com.restaurant.management.exception.LoginRateLimitExceededException;
import com.restaurant.management.service.JournalAuthentificationService;
import com.restaurant.management.service.UtilisateurAuthentifieService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
public class SessionAuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final LoginAttemptService loginAttemptService;
    private final JournalAuthentificationService journalAuthentificationService;
    private final UtilisateurAuthentifieService utilisateurAuthentifieService;

    public SessionAuthenticationService(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            LoginAttemptService loginAttemptService,
            JournalAuthentificationService journalAuthentificationService,
            UtilisateurAuthentifieService utilisateurAuthentifieService
    ) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.loginAttemptService = loginAttemptService;
        this.journalAuthentificationService = journalAuthentificationService;
        this.utilisateurAuthentifieService = utilisateurAuthentifieService;
    }

    public UtilisateurAuthentifieResponse login(
            ConnexionRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String email = requestBody.email().strip().toLowerCase(Locale.ROOT);
        String remoteAddress = request.getRemoteAddr();
        UUID correlationId = UUID.randomUUID();

        if (loginAttemptService.isBlocked(email, remoteAddress)) {
            journalAuthentificationService.journaliserConnexionEchouee(remoteAddress, correlationId);
            throw new LoginRateLimitExceededException();
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, requestBody.motDePasse())
            );
        } catch (AuthenticationException exception) {
            boolean blocked = loginAttemptService.registerFailure(email, remoteAddress);
            journalAuthentificationService.journaliserConnexionEchouee(remoteAddress, correlationId);
            if (blocked) {
                throw new LoginRateLimitExceededException();
            }
            throw new AuthenticationFailedException();
        }

        RestaurantUserPrincipal principal = (RestaurantUserPrincipal) authentication.getPrincipal();
        loginAttemptService.reset(email, remoteAddress);
        journalAuthentificationService.journaliserConnexionReussie(
                principal.getUtilisateurId(),
                remoteAddress,
                correlationId
        );

        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);

        return utilisateurAuthentifieService.getById(principal.getUtilisateurId());
    }
}
