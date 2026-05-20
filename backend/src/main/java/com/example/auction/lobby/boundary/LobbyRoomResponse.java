package com.example.auction.lobby.boundary;

import com.example.auction.session.entity.GameMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LobbyRoomResponse(
    UUID sessionId,
    String status,
    int playerCount,
    int maxPlayers,
    GameMode gameMode,
    List<String> playerNicknames,
    Instant createdAt,
    int spectatorCount
) {
}
