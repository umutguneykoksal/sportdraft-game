package com.example.auction.bidding.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "card")
public class Card {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Position position;

    @Column(nullable = false)
    private int rating;

    protected Card() {
    }

    public Card(UUID id, String name, Position position, int rating) {
        this.id = id;
        this.name = name;
        this.position = position;
        this.rating = rating;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Position getPosition() {
        return position;
    }

    public int getRating() {
        return rating;
    }
}
