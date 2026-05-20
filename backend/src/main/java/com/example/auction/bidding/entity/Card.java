package com.example.auction.bidding.entity;

import com.example.auction.session.entity.PoolLeague;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    @Column(nullable = false)
    private int pace;

    @Column(nullable = false)
    private int shooting;

    @Column(nullable = false)
    private int passing;

    @Column(nullable = false)
    private int defending;

    @Column(nullable = false)
    private int physical;

    @Column(name = "club_name", length = 120)
    private String clubName;

    @Enumerated(EnumType.STRING)
    @Column(name = "pool_league", nullable = false, length = 32)
    private PoolLeague poolLeague = PoolLeague.PREMIER_LEAGUE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "card_eligible_position", joinColumns = @JoinColumn(name = "card_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "position", length = 10)
    private Set<Position> eligiblePositions = new HashSet<>();

    protected Card() {
    }

    public Card(UUID id, String name, Position position, int rating) {
        this(id, name, position, rating, Set.of(position));
    }

    public Card(UUID id, String name, Position position, int rating, Set<Position> eligiblePositions) {
        this(id, name, position, rating, eligiblePositions, PlayerAttributes.fromOverall(position, rating));
    }

    public Card(UUID id, String name, Position position, int rating, Set<Position> eligiblePositions,
                PlayerAttributes attributes) {
        this.id = id;
        this.name = name;
        this.position = position;
        this.rating = rating;
        this.eligiblePositions = new HashSet<>(eligiblePositions);
        this.pace = attributes.pace();
        this.shooting = attributes.shooting();
        this.passing = attributes.passing();
        this.defending = attributes.defending();
        this.physical = attributes.physical();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getClubName() {
        return clubName;
    }

    public PoolLeague getPoolLeague() {
        return poolLeague != null ? poolLeague : PoolLeague.PREMIER_LEAGUE;
    }

    public Position getPosition() {
        return position;
    }

    public int getRating() {
        return rating;
    }

    public PlayerAttributes getAttributes() {
        return new PlayerAttributes(pace, shooting, passing, defending, physical);
    }

    public Set<Position> getEligiblePositions() {
        if (eligiblePositions == null || eligiblePositions.isEmpty()) {
            return Set.of(position);
        }
        return Collections.unmodifiableSet(eligiblePositions);
    }

    public boolean canPlay(Position pos) {
        return getEligiblePositions().contains(pos);
    }

    /** Primary position first, then other eligible positions alphabetically. */
    public List<String> orderedEligiblePositionNames() {
        List<String> names = new ArrayList<>();
        names.add(position.name());
        getEligiblePositions().stream()
            .filter(pos -> pos != position)
            .sorted(Comparator.comparing(Enum::name))
            .map(Position::name)
            .forEach(names::add);
        return names;
    }
}
