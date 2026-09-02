package com.restaurant.management.entity;

import com.restaurant.management.enums.TypeNotificationInterne;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "notifications_internes")
public class NotificationInterne {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TypeNotificationInterne type;

    @Column(nullable = false, length = 255)
    private String message;

    @Column(nullable = false)
    private boolean lue;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe;

    @Version
    @Column(nullable = false)
    private long version;

    protected NotificationInterne() {
    }

    public NotificationInterne(Commande commande, TypeNotificationInterne type, String message) {
        this.commande = commande;
        this.type = type;
        this.message = message;
    }

    @PrePersist
    void initialiserDate() {
        creeLe = Instant.now();
    }

    public Long getId() { return id; }
    public Commande getCommande() { return commande; }
    public TypeNotificationInterne getType() { return type; }
    public String getMessage() { return message; }
    public boolean isLue() { return lue; }
    public Instant getCreeLe() { return creeLe; }
    public long getVersion() { return version; }
}
