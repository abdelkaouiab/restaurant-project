package com.restaurant.management.entity;

import com.restaurant.management.enums.StatutReservation;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "reservations")
public class Reservation {
    public static final Duration DUREE = Duration.ofHours(2);

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 50, updatable = false)
    private String numero;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private TableRestaurant table;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cree_par_id", nullable = false, updatable = false)
    private Utilisateur creePar;
    @Column(name = "nombre_personnes", nullable = false)
    private int nombrePersonnes;
    @Column(nullable = false, updatable = false)
    private Instant debut;
    @Column(nullable = false, updatable = false)
    private Instant fin;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutReservation statut = StatutReservation.EN_ATTENTE;
    @Column(length = 500)
    private String remarques;
    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe;
    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;
    @Version @Column(nullable = false)
    private long version;

    protected Reservation() {}

    public Reservation(String numero, TableRestaurant table, Client client, Utilisateur creePar,
                       int nombrePersonnes, Instant debut, String remarques) {
        this.numero = numero;
        this.table = table;
        this.client = client;
        this.creePar = creePar;
        this.nombrePersonnes = nombrePersonnes;
        this.debut = debut;
        this.fin = debut.plus(DUREE);
        this.remarques = remarques == null || remarques.isBlank() ? null : remarques.strip();
    }

    public void confirmer() { exiger(StatutReservation.EN_ATTENTE); statut = StatutReservation.CONFIRMEE; }
    public void enregistrerArrivee() { exiger(StatutReservation.CONFIRMEE); statut = StatutReservation.ARRIVEE; }
    public void terminer() { exiger(StatutReservation.ARRIVEE); statut = StatutReservation.TERMINEE; }
    public void annuler() {
        if (statut != StatutReservation.EN_ATTENTE && statut != StatutReservation.CONFIRMEE)
            throw new IllegalStateException("Cette réservation ne peut pas être annulée.");
        statut = StatutReservation.ANNULEE;
    }
    public void marquerAbsente() { exiger(StatutReservation.CONFIRMEE); statut = StatutReservation.ABSENTE; }
    private void exiger(StatutReservation attendu) {
        if (statut != attendu) throw new IllegalStateException("Transition de réservation interdite.");
    }
    @PrePersist void creerDates() { creeLe = Instant.now(); modifieLe = creeLe; }
    @PreUpdate void modifierDate() { modifieLe = Instant.now(); }

    public Long getId() { return id; }
    public String getNumero() { return numero; }
    public TableRestaurant getTable() { return table; }
    public Client getClient() { return client; }
    public Utilisateur getCreePar() { return creePar; }
    public int getNombrePersonnes() { return nombrePersonnes; }
    public Instant getDebut() { return debut; }
    public Instant getFin() { return fin; }
    public StatutReservation getStatut() { return statut; }
    public String getRemarques() { return remarques; }
    public Instant getCreeLe() { return creeLe; }
    public Instant getModifieLe() { return modifieLe; }
    public long getVersion() { return version; }
}
