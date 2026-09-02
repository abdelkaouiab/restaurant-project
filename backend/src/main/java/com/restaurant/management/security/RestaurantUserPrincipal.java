package com.restaurant.management.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

public final class RestaurantUserPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long utilisateurId;
    private final String email;
    private String motDePasseHash;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;
    private final long utilisateurVersion;
    private final long employeVersion;
    private final boolean validationSessionRequise;

    public RestaurantUserPrincipal(
            Long utilisateurId,
            String email,
            String motDePasseHash,
            boolean enabled,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this(utilisateurId, email, motDePasseHash, enabled, authorities, -1, -1, false);
    }

    public RestaurantUserPrincipal(
            Long utilisateurId,
            String email,
            String motDePasseHash,
            boolean enabled,
            Collection<? extends GrantedAuthority> authorities,
            long utilisateurVersion,
            long employeVersion,
            boolean validationSessionRequise
    ) {
        this.utilisateurId = utilisateurId;
        this.email = email;
        this.motDePasseHash = motDePasseHash;
        this.enabled = enabled;
        this.authorities = List.copyOf(authorities);
        this.utilisateurVersion = utilisateurVersion;
        this.employeVersion = employeVersion;
        this.validationSessionRequise = validationSessionRequise;
    }

    public Long getUtilisateurId() {
        return utilisateurId;
    }

    public long getUtilisateurVersion() { return utilisateurVersion; }

    public long getEmployeVersion() { return employeVersion; }

    public boolean isValidationSessionRequise() { return validationSessionRequise; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return motDePasseHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        motDePasseHash = null;
    }
}
