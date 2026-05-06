package com.example.auction.session.entity;

import com.example.auction.bidding.entity.Card;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "session_player")
public class SessionPlayer {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Formation formation;

    @Column(name = "remaining_budget_cents", nullable = false)
    private long remainingBudgetCents;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "session_player_cards",
        joinColumns = @JoinColumn(name = "session_player_id"),
        inverseJoinColumns = @JoinColumn(name = "card_id")
    )
    private Set<Card> cards = new HashSet<>();

    protected SessionPlayer() {
    }

    public SessionPlayer(UUID id, GameSession session, UUID userId, Formation formation, long remainingBudgetCents) {
        this.id = id;
        this.session = session;
        this.userId = userId;
        this.formation = formation;
        this.remainingBudgetCents = remainingBudgetCents;
    }

    public UUID getId() {
        return id;
    }

    public GameSession getSession() {
        return session;
    }

    public UUID getUserId() {
        return userId;
    }

    public Formation getFormation() {
        return formation;
    }

    public long getRemainingBudgetCents() {
        return remainingBudgetCents;
    }

    public Set<Card> getCards() {
        return cards;
    }

    public void setFormation(Formation formation) {
        this.formation = formation;
    }

    public void spendBudget(long amountCents) {
        this.remainingBudgetCents -= amountCents;
    }

    public void addCard(Card card) {
        this.cards.add(card);
    }
}
