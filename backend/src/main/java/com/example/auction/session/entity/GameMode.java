package com.example.auction.session.entity;

public enum GameMode {
    DUEL(2),
    TOURNAMENT(4),
    LEAGUE(4);

    private final int maxPlayers;

    GameMode(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public static GameMode fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            return DUEL;
        }
        return GameMode.valueOf(raw.trim().toUpperCase());
    }
}
