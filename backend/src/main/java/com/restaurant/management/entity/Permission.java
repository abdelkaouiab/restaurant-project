package com.restaurant.management.entity;

import com.restaurant.management.enums.NomPermission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "permissions")
public class Permission extends AbstractActiveEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "nom", nullable = false, length = 80)
    private NomPermission nom;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    protected Permission() {
    }

    public Permission(NomPermission nom, String description) {
        this.nom = nom;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public NomPermission getNom() {
        return nom;
    }

    public String getDescription() {
        return description;
    }
}
