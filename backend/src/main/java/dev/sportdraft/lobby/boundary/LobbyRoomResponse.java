package dev.sportdraft.lobby.boundary;

import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.entity.PoolLeague;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LobbyRoomResponse(
    UUID sessionId,
    String status,
    int playerCount,
    int maxPlayers,
    GameMode gameMode,
    int minPoolRating,
    PoolLeague poolLeague,
    List<String> playerNicknames,
    Instant createdAt,
    int spectatorCount
) {
}
