package com.restaurant.management.security;

import com.restaurant.management.service.JournalAuthentificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class RestLogoutSuccessHandler implements LogoutSuccessHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestLogoutSuccessHandler.class);

    private final JournalAuthentificationService journalAuthentificationService;

    public RestLogoutSuccessHandler(JournalAuthentificationService journalAuthentificationService) {
        this.journalAuthentificationService = journalAuthentificationService;
    }

    @Override
    public void onLogoutSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        if (authentication != null && authentication.getPrincipal() instanceof RestaurantUserPrincipal principal) {
            try {
                journalAuthentificationService.journaliserDeconnexion(
                        principal.getUtilisateurId(),
                        request.getRemoteAddr(),
                        UUID.randomUUID()
                );
            } catch (RuntimeException exception) {
                LOGGER.error("Échec de la journalisation de la déconnexion (type: {})",
                        exception.getClass().getSimpleName());
            }
        }
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
