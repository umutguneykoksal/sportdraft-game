package com.example.auction.lobby.boundary;

import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.SessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionSnapshotResponse(
    UUID sessionId,
    SessionStatus status,
    Instant countdownEndsAt,
    int playerCount,
    boolean joined,
    Formation myFormation,
    Instant matchStartDeadlineAt,
    int matchStartReadyCount,
    boolean matchStartConfirmedByMe,
    long version,
    long lastSeq,
    GameMode gameMode,
    int maxPlayers,
    List<LobbyPlayerView> players
) {
}
