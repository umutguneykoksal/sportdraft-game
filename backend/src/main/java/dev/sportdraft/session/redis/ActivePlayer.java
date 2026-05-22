package dev.sportdraft.session.redis;

import dev.sportdraft.bidding.entity.Position;
import dev.sportdraft.session.entity.Formation;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ActivePlayer {

    private static final int MAX_ROSTER_SIZE = 11;

    private UUID id;
    private UUID userId;
    private String nickname;
    private Formation formation;
    private boolean formationConfirmed;
    private long remainingBudgetCents;
    private List<UUID> cardIds = new ArrayList<>();
    /** Formation slot filled when the card was won (may differ from card primary position). */
    private Map<UUID, Position> cardAssignments = new HashMap<>();

    public ActivePlayer() {
    }

    public ActivePlayer(UUID id, UUID userId, String nickname, Formation formation, long remainingBudgetCents) {
        this.id = id;
        this.userId = userId;
        this.nickname = nickname;
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

    public void addCard(UUID cardId, Position assignedPosition) {
        if (cardIds.size() >= MAX_ROSTER_SIZE) {
            throw new IllegalStateException("Roster is already full");
        }
        this.cardIds.add(cardId);
        this.cardAssignments.put(cardId, assignedPosition);
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
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Formation getFormation() { return formation; }
    public void setFormation(Formation formation) { this.formation = formation; }
    public boolean isFormationConfirmed() { return formationConfirmed; }
    public void setFormationConfirmed(boolean formationConfirmed) { this.formationConfirmed = formationConfirmed; }
    public long getRemainingBudgetCents() { return remainingBudgetCents; }
    public void setRemainingBudgetCents(long remainingBudgetCents) { this.remainingBudgetCents = remainingBudgetCents; }
    public List<UUID> getCardIds() { return cardIds; }
    public void setCardIds(List<UUID> cardIds) { this.cardIds = cardIds; }
    public Map<UUID, Position> getCardAssignments() { return cardAssignments; }
    public void setCardAssignments(Map<UUID, Position> cardAssignments) {
        this.cardAssignments = cardAssignments != null ? cardAssignments : new HashMap<>();
    }
}
