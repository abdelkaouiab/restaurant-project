package com.restaurant.management.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionSecurityValidationFilterTest {

    @AfterEach
    void nettoyerContexte() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void laissePasserUneSessionDontLEtatSecuriteEstInchange() throws Exception {
        RestaurantUserDetailsService detailsService = mock(RestaurantUserDetailsService.class);
        RestaurantUserPrincipal session = principal(4, 7, "COMMANDE_CONSULTER");
        RestaurantUserPrincipal courant = principal(4, 7, "COMMANDE_CONSULTER");
        when(detailsService.loadUserByUsername(session.getUsername())).thenReturn(courant);
        SessionSecurityValidationFilter filtre = filtre(detailsService);
        FilterChain chaine = mock(FilterChain.class);

        MockHttpServletRequest requete = requeteAuthentifiee(session);
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete, reponse, chaine);

        verify(chaine).doFilter(requete, reponse);
        assertThat(reponse.getStatus()).isEqualTo(200);
    }

    @Test
    void revoqueLaSessionApresChangementDeMotDePasseRoleOuStatut() throws Exception {
        RestaurantUserDetailsService detailsService = mock(RestaurantUserDetailsService.class);
        RestaurantUserPrincipal session = principal(4, 7, "COMMANDE_CONSULTER");
        RestaurantUserPrincipal courant = principal(5, 7, "COMMANDE_CONSULTER");
        when(detailsService.loadUserByUsername(session.getUsername())).thenReturn(courant);
        SessionSecurityValidationFilter filtre = filtre(detailsService);
        FilterChain chaine = mock(FilterChain.class);

        MockHttpServletRequest requete = requeteAuthentifiee(session);
        MockHttpSession httpSession = (MockHttpSession) requete.getSession(false);
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete, reponse, chaine);

        verify(chaine, never()).doFilter(requete, reponse);
        assertThat(reponse.getStatus()).isEqualTo(401);
        assertThat(reponse.getContentAsString()).contains("UNAUTHORIZED").doesNotContain("Session révoquée");
        assertThat(httpSession.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private SessionSecurityValidationFilter filtre(RestaurantUserDetailsService service) {
        return new SessionSecurityValidationFilter(
                service,
                new RestAuthenticationEntryPoint(new ObjectMapper().findAndRegisterModules())
        );
    }

    private MockHttpServletRequest requeteAuthentifiee(RestaurantUserPrincipal principal) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockHttpServletRequest requete = new MockHttpServletRequest("GET", "/api/auth/me");
        requete.setSession(new MockHttpSession());
        return requete;
    }

    private RestaurantUserPrincipal principal(long utilisateurVersion, long employeVersion, String autorite) {
        return new RestaurantUserPrincipal(
                12L,
                "session@example.com",
                null,
                true,
                List.of(new SimpleGrantedAuthority(autorite)),
                utilisateurVersion,
                employeVersion,
                true
        );
    }
}
