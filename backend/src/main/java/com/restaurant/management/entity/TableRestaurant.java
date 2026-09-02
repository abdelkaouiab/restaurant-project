package com.restaurant.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tables_restaurant")
public class TableRestaurant extends AbstractActiveEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int numero;

    @Column(nullable = false)
    private int capacite;

    protected TableRestaurant() {
    }

    public TableRestaurant(int numero, int capacite) {
        modifier(numero, capacite);
    }

    public void modifier(int numero, int capacite) {
        this.numero = numero;
        this.capacite = capacite;
    }

    public Long getId() { return id; }
    public int getNumero() { return numero; }
    public int getCapacite() { return capacite; }
}
