package com.example.auction.competition.entity;

public enum LeaguePhase {
    MATCHDAY_1,
    BREAK_AFTER_MD1,
    MATCHDAY_2,
    BREAK_AFTER_MD2,
    MATCHDAY_3,
    CHAMPIONSHIP_PLAYOFF,
    COMPLETED,
    REPLAYS;

    public static LeaguePhase effective(LeaguePhase stored) {
        return stored != null ? stored : MATCHDAY_1;
    }

    public boolean isMatchdayBreak() {
        return this == BREAK_AFTER_MD1 || this == BREAK_AFTER_MD2;
    }

    public int matchdayNumber() {
        return switch (this) {
            case MATCHDAY_1, BREAK_AFTER_MD1 -> 1;
            case MATCHDAY_2, BREAK_AFTER_MD2 -> 2;
            case MATCHDAY_3 -> 3;
            default -> 0;
        };
    }

    public LeaguePhase nextMatchdayPhase() {
        return switch (this) {
            case BREAK_AFTER_MD1 -> MATCHDAY_2;
            case BREAK_AFTER_MD2 -> MATCHDAY_3;
            default -> this;
        };
    }

    public LeaguePhase breakAfterMatchday() {
        return switch (this) {
            case MATCHDAY_1 -> BREAK_AFTER_MD1;
            case MATCHDAY_2 -> BREAK_AFTER_MD2;
            default -> this;
        };
    }
}
