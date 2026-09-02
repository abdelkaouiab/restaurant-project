package com.restaurant.management.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/** Révoque une session dès que le compte, l'employé, le mot de passe ou les droits changent. */
@Component
public class SessionSecurityValidationFilter extends OncePerRequestFilter {
    private final RestaurantUserDetailsService userDetailsService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public SessionSecurityValidationFilter(
            RestaurantUserDetailsService userDetailsService,
            RestAuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.userDetailsService = userDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof RestaurantUserPrincipal sessionPrincipal)
                || !sessionPrincipal.isValidationSessionRequise()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            UserDetails details = userDetailsService.loadUserByUsername(sessionPrincipal.getUsername());
            RestaurantUserPrincipal courant = (RestaurantUserPrincipal) details;
            if (!memeEtatSecurite(sessionPrincipal, courant)) {
                refuser(request, response);
                return;
            }
        } catch (AuthenticationException exception) {
            refuser(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean memeEtatSecurite(RestaurantUserPrincipal session, RestaurantUserPrincipal courant) {
        return courant.isEnabled()
                && session.getUtilisateurId().equals(courant.getUtilisateurId())
                && session.getUtilisateurVersion() == courant.getUtilisateurVersion()
                && session.getEmployeVersion() == courant.getEmployeVersion()
                && autorites(session).equals(autorites(courant));
    }

    private Set<String> autorites(RestaurantUserPrincipal principal) {
        return principal.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toUnmodifiableSet());
    }

    private void refuser(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(request, response, new SessionRevoqueeException());
    }

    private static final class SessionRevoqueeException extends AuthenticationException {
        private SessionRevoqueeException() { super("Session révoquée."); }
    }
}
