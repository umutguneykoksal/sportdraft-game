package com.example.auction.session.redis;

import com.example.auction.session.entity.Formation;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ActivePlayer {

    private static final int MAX_ROSTER_SIZE = 11;

    private UUID id;
    private UUID userId;
    private Formation formation;
    private boolean formationConfirmed;
    private long remainingBudgetCents;
    private List<UUID> cardIds = new ArrayList<>();

    public ActivePlayer() {
    }

    public ActivePlayer(UUID id, UUID userId, Formation formation, long remainingBudgetCents) {
        this.id = id;
        this.userId = userId;
        this.formation = formation;
        this.formationConfirmed = false;
        this.remainingBudgetCents = remainingBudgetCents;
    }

    // --- Domain methods (mirror JPA SessionPlayer) ---

    public void selectFormation(Formation formation) {
        this.formation = formation;
        this.formationConfirmed = true;
    }

    public void confirmDefaultFormationIfMissing() {
        if (!this.formationConfirmed) {
            this.formation = Formation.FOUR_FOUR_TWO;
            this.formationConfirmed = true;
        }
    }

    public void spendBudget(long amountCents) {
        this.remainingBudgetCents -= amountCents;
    }

    public void addCard(UUID cardId) {
        if (cardIds.size() >= MAX_ROSTER_SIZE) {
            throw new IllegalStateException("Roster is already full");
        }
        this.cardIds.add(cardId);
    }

    @JsonIgnore
    public int cardCount() {
        return cardIds.size();
    }

    @JsonIgnore
    public boolean isRosterFull() {
        return cardIds.size() >= MAX_ROSTER_SIZE;
    }

    // --- Getters / Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public Formation getFormation() { return formation; }
    public void setFormation(Formation formation) { this.formation = formation; }
    public boolean isFormationConfirmed() { return formationConfirmed; }
    public void setFormationConfirmed(boolean formationConfirmed) { this.formationConfirmed = formationConfirmed; }
    public long getRemainingBudgetCents() { return remainingBudgetCents; }
    public void setRemainingBudgetCents(long remainingBudgetCents) { this.remainingBudgetCents = remainingBudgetCents; }
    public List<UUID> getCardIds() { return cardIds; }
    public void setCardIds(List<UUID> cardIds) { this.cardIds = cardIds; }
}
