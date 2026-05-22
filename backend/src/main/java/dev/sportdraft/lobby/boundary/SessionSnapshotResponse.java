package dev.sportdraft.lobby.boundary;

import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.entity.PoolLeague;
import dev.sportdraft.session.entity.SessionStatus;
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
    int minPoolRating,
    PoolLeague poolLeague,
    List<LobbyPlayerView> players
) {
}
