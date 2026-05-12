package com.example.auction.lobby.control;

import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.lobby.boundary.SessionSnapshotResponse;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LobbyService {

    private static final long STARTING_BUDGET_CENTS = 100L;

    private final ActiveSessionStore sessionStore;
    private final SseEmitterRegistry sseEmitterRegistry;

    public LobbyService(ActiveSessionStore sessionStore, SseEmitterRegistry sseEmitterRegistry) {
        this.sessionStore = sessionStore;
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    public SessionSnapshotResponse joinLobby(UUID userId) {
        List<ActiveSession> waitingSessions = sessionStore.findWaitingSessions();
        ActiveSession target = waitingSessions.stream()
            .filter(s -> s.playerCount() < 2)
            .findFirst()
            .orElseGet(() -> {
                ActiveSession created = new ActiveSession(UUID.randomUUID(), SessionStatus.WAITING, Instant.now());
                sessionStore.save(created);
                return created;
            });

        if (target.playerCount() >= 2 && !target.hasPlayerWithUserId(userId)) {
            target = new ActiveSession(UUID.randomUUID(), SessionStatus.WAITING, Instant.now());
            sessionStore.save(target);
        }

        long versionBefore = target.getVersion();
        if (!target.hasPlayerWithUserId(userId)) {
            ActivePlayer player = new ActivePlayer(
                UUID.randomUUID(), userId, Formation.FOUR_FOUR_TWO, STARTING_BUDGET_CENTS
            );
            target.getPlayers().add(player);
        }

        if (target.playerCount() == 2 && target.getStatus() == SessionStatus.WAITING) {
            target.moveToCountdown(Instant.now().plus(5, ChronoUnit.SECONDS));
            sessionStore.saveIfVersionMatch(target, versionBefore);
            sseEmitterRegistry.broadcast(
                target.getId(),
                "countdown_started",
                Map.of("sessionId", target.getId(), "endsAt", target.getCountdownEndsAt())
            );
        } else {
            sessionStore.saveIfVersionMatch(target, versionBefore);
        }

        sseEmitterRegistry.broadcast(
            target.getId(),
            "lobby_updated",
            Map.of("sessionId", target.getId(), "playerCount", target.playerCount(), "status", target.getStatus())
        );
        return snapshot(target, userId);
    }

    public SessionSnapshotResponse leaveLobby(UUID sessionId, UUID userId) {
        ActiveSession session = sessionStore.getById(sessionId);
        long versionBefore = session.getVersion();

        ActivePlayer player = session.findPlayerByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not in session"));

        session.getPlayers().remove(player);

        if (session.playerCount() < 2 && session.getStatus() == SessionStatus.COUNTDOWN) {
            session.moveToWaiting();
            sessionStore.saveIfVersionMatch(session, versionBefore);
            sseEmitterRegistry.broadcast(
                sessionId, "countdown_cancelled",
                Map.of("sessionId", sessionId, "remainingPlayers", session.playerCount())
            );
        } else {
            sessionStore.saveIfVersionMatch(session, versionBefore);
        }

        sseEmitterRegistry.broadcast(
            sessionId, "lobby_updated",
            Map.of("sessionId", sessionId, "playerCount", session.playerCount(), "status", session.getStatus())
        );
        return snapshot(session, userId);
    }

    public SessionSnapshotResponse selectFormation(UUID sessionId, UUID userId, Formation formation) {
        ActiveSession session = sessionStore.getById(sessionId);
        long versionBefore = session.getVersion();

        if (session.getStatus() != SessionStatus.FORMATION_SELECT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is not in formation selection phase");
        }

        ActivePlayer player = session.findPlayerByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not in session"));

        player.selectFormation(formation);

        sseEmitterRegistry.broadcast(
            sessionId, "formation_chosen",
            Map.of("sessionId", sessionId, "userId", userId, "formation", formation)
        );

        if (session.playerCount() == 2
            && session.getPlayers().stream().allMatch(ActivePlayer::isFormationConfirmed)) {
            session.moveToAuctioning();
            sessionStore.saveIfVersionMatch(session, versionBefore);
            sseEmitterRegistry.broadcast(sessionId, "auctioning_started", Map.of("sessionId", sessionId));
        } else {
            sessionStore.saveIfVersionMatch(session, versionBefore);
        }

        return snapshot(session, userId);
    }

    public SessionSnapshotResponse getSnapshot(UUID sessionId, UUID userId) {
        ActiveSession session = sessionStore.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        return snapshot(session, userId);
    }

    public void advanceTimedPhases() {
        Instant now = Instant.now();

        for (ActiveSession session : sessionStore.findByStatus(SessionStatus.COUNTDOWN)) {
            if (session.getCountdownEndsAt() != null && !session.getCountdownEndsAt().isAfter(now)) {
                long vBefore = session.getVersion();
                if (session.playerCount() == 2) {
                    session.moveToFormationSelect(now.plus(30, ChronoUnit.SECONDS));
                    sessionStore.saveIfVersionMatch(session, vBefore);
                    sseEmitterRegistry.broadcast(
                        session.getId(), "formation_phase_started",
                        Map.of("sessionId", session.getId(), "endsAt", session.getCountdownEndsAt())
                    );
                } else {
                    session.moveToWaiting();
                    sessionStore.saveIfVersionMatch(session, vBefore);
                }
            }
        }

        for (ActiveSession session : sessionStore.findByStatus(SessionStatus.FORMATION_SELECT)) {
            if (session.getCountdownEndsAt() != null && !session.getCountdownEndsAt().isAfter(now)) {
                long vBefore = session.getVersion();
                if (session.playerCount() == 2) {
                    session.getPlayers().forEach(ActivePlayer::confirmDefaultFormationIfMissing);
                    session.moveToAuctioning();
                    sessionStore.saveIfVersionMatch(session, vBefore);
                    sseEmitterRegistry.broadcast(
                        session.getId(), "auctioning_started",
                        Map.of("sessionId", session.getId())
                    );
                } else {
                    session.moveToWaiting();
                    sessionStore.saveIfVersionMatch(session, vBefore);
                }
            }
        }
    }

    private SessionSnapshotResponse snapshot(ActiveSession session, UUID userId) {
        ActivePlayer me = session.findPlayerByUserId(userId).orElse(null);

        Instant matchStartDeadlineAt = null;
        int matchStartReadyCount = 0;
        boolean matchStartConfirmedByMe = false;
        if (session.getStatus() == SessionStatus.MATCH_START_PENDING) {
            matchStartDeadlineAt = session.getMatchStartDeadlineAt();
            matchStartReadyCount = session.matchStartReadyCount();
            matchStartConfirmedByMe = userId.equals(session.getMatchStartReadyUserOneId())
                || userId.equals(session.getMatchStartReadyUserTwoId());
        }

        return new SessionSnapshotResponse(
            session.getId(),
            session.getStatus(),
            session.getCountdownEndsAt(),
            session.playerCount(),
            me != null,
            me != null && me.isFormationConfirmed() ? me.getFormation() : null,
            matchStartDeadlineAt,
            matchStartReadyCount,
            matchStartConfirmedByMe
        );
    }
}
