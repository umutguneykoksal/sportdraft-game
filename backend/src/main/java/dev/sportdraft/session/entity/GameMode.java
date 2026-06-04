package dev.sportdraft.session.entity;

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

    /** @throws org.springframework.web.server.ResponseStatusException 400 when unknown */
    public static GameMode fromWireOrThrow(String raw) {
        if (raw == null || raw.isBlank()) {
            return DUEL;
        }
        try {
            return GameMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Unknown gameMode: " + raw
            );
        }
    }
}
