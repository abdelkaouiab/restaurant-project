package com.restaurant.management.security;

import com.restaurant.management.entity.Permission;
import com.restaurant.management.entity.Role;
import com.restaurant.management.entity.Utilisateur;
import com.restaurant.management.repository.UtilisateurRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class RestaurantUserDetailsService implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;

    public RestaurantUserDetailsService(UtilisateurRepository utilisateurRepository) {
        this.utilisateurRepository = utilisateurRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        Utilisateur utilisateur = utilisateurRepository
                .findWithEmployeAndRolesByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Identifiants invalides"));

        Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();
        utilisateur.getRoles().stream()
                .filter(Role::isActif)
                .forEach(role -> {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getNom().name()));
                    role.getPermissions().stream()
                            .filter(Permission::isActif)
                            .map(permission -> new SimpleGrantedAuthority(permission.getNom().name()))
                            .forEach(authorities::add);
                });

        boolean enabled = utilisateur.isActif() && utilisateur.getEmploye().isActif();
        return new RestaurantUserPrincipal(
                utilisateur.getId(),
                utilisateur.getEmail(),
                utilisateur.getMotDePasseHash(),
                enabled,
                authorities,
                utilisateur.getVersion(),
                utilisateur.getEmploye().getVersion(),
                true
        );
    }
}
