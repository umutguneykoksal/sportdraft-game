package com.example.auction.lobby.boundary;

import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.SessionStatus;
import java.time.Instant;
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
    /** Monoton Redis version of the session; clients use this to detect stale snapshots. */
    long version,
    /** Last event seq number at the time this snapshot was taken; clients use for gap detection. */
    long lastSeq
) {
}
