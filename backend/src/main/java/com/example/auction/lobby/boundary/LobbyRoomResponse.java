package com.example.auction.lobby.boundary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LobbyRoomResponse(
    UUID sessionId,
    String status,
    int playerCount,
    List<String> playerNicknames,
    Instant createdAt,
    int spectatorCount
) {}
