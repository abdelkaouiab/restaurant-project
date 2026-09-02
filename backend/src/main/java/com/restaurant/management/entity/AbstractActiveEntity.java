package com.restaurant.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

import java.time.Instant;

@MappedSuperclass
public abstract class AbstractActiveEntity {

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe;

    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @PrePersist
    protected void initializeTimestamps() {
        Instant now = Instant.now();
        creeLe = now;
        modifieLe = now;
    }

    @PreUpdate
    protected void updateTimestamp() {
        modifieLe = Instant.now();
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }

    public Instant getCreeLe() {
        return creeLe;
    }

    public Instant getModifieLe() {
        return modifieLe;
    }

    public long getVersion() {
        return version;
    }
}
