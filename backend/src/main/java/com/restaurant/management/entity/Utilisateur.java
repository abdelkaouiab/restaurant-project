package com.restaurant.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.Collections;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(name = "utilisateurs")
public class Utilisateur extends AbstractActiveEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "mot_de_passe_hash", nullable = false, length = 255)
    private String motDePasseHash;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "utilisateur_roles",
            joinColumns = @JoinColumn(name = "utilisateur_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    protected Utilisateur() {
    }

    public Utilisateur(String email, String motDePasseHash, Employe employe) {
        this.email = normalizeEmail(email);
        this.motDePasseHash = motDePasseHash;
        this.employe = employe;
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getMotDePasseHash() {
        return motDePasseHash;
    }

    public Employe getEmploye() {
        return employe;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public void addRole(Role role) {
        roles.add(role);
    }

    public void modifierEmail(String email) {
        this.email = normalizeEmail(email);
    }

    public void remplacerRoles(Collection<Role> roles) {
        this.roles.clear();
        this.roles.addAll(roles);
    }

    public void remplacerMotDePasseHash(String motDePasseHash) {
        this.motDePasseHash = motDePasseHash;
    }
}
