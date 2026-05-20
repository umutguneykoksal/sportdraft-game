package com.example.auction.competition.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class CompetitionMatchViewId implements Serializable {

    private UUID matchId;
    private UUID userId;

    public CompetitionMatchViewId() {}

    public CompetitionMatchViewId(UUID matchId, UUID userId) {
        this.matchId = matchId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompetitionMatchViewId that = (CompetitionMatchViewId) o;
        return Objects.equals(matchId, that.matchId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchId, userId);
    }
}
