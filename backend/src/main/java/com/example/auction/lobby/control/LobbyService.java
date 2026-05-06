package com.example.auction.lobby.control;

import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.lobby.boundary.SessionSnapshotResponse;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameSession;
import com.example.auction.session.entity.GameSessionRepository;
import com.example.auction.session.entity.SessionPlayer;
import com.example.auction.session.entity.SessionPlayerRepository;
import com.example.auction.session.entity.SessionStatus;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class LobbyService {

    private static final long STARTING_BUDGET_CENTS = 10_000L;

    private final GameSessionRepository gameSessionRepository;
    private final SessionPlayerRepository sessionPlayerRepository;
    private final SseEmitterRegistry sseEmitterRegistry;

    public LobbyService(
        GameSessionRepository gameSessionRepository,
        SessionPlayerRepository sessionPlayerRepository,
        SseEmitterRegistry sseEmitterRegistry
    ) {
        this.gameSessionRepository = gameSessionRepository;
        this.sessionPlayerRepository = sessionPlayerRepository;
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    public SessionSnapshotResponse joinLobby(UUID userId) {
        List<GameSession> waitingSessions = gameSessionRepository.findByStatusOrderByCreatedAtAsc(SessionStatus.WAITING);
        GameSession target = waitingSessions.stream()
            .filter(session -> sessionPlayerRepository.countBySession_Id(session.getId()) < 2)
            .findFirst()
            .orElseGet(() -> {
                GameSession created = new GameSession(UUID.randomUUID(), SessionStatus.WAITING, Instant.now());
                return gameSessionRepository.save(created);
            });

        if (!sessionPlayerRepository.existsBySession_IdAndUserId(target.getId(), userId)) {
            SessionPlayer player = new SessionPlayer(
                UUID.randomUUID(),
                target,
                userId,
                Formation.FOUR_FOUR_TWO,
                STARTING_BUDGET_CENTS
            );
            sessionPlayerRepository.save(player);
        }

        long playerCount = sessionPlayerRepository.countBySession_Id(target.getId());
        if (playerCount == 2 && target.getStatus() == SessionStatus.WAITING) {
            target.moveToCountdown(Instant.now().plus(5, ChronoUnit.SECONDS));
            sseEmitterRegistry.broadcast(
                target.getId(),
                "countdown_started",
                Map.of("sessionId", target.getId(), "endsAt", target.getCountdownEndsAt())
            );
        }

        sseEmitterRegistry.broadcast(
            target.getId(),
            "lobby_updated",
            Map.of("sessionId", target.getId(), "playerCount", playerCount, "status", target.getStatus())
        );
        return snapshot(target.getId(), userId);
    }

    public SessionSnapshotResponse leaveLobby(UUID sessionId, UUID userId) {
        GameSession session = gameSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        SessionPlayer player = sessionPlayerRepository.findBySession_IdAndUserId(sessionId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not in session"));

        sessionPlayerRepository.delete(player);

        long remaining = sessionPlayerRepository.countBySession_Id(sessionId);
        if (remaining < 2 && session.getStatus() == SessionStatus.COUNTDOWN) {
            session.moveToWaiting();
            sseEmitterRegistry.broadcast(
                sessionId,
                "countdown_cancelled",
                Map.of("sessionId", sessionId, "remainingPlayers", remaining)
            );
        }

        sseEmitterRegistry.broadcast(
            sessionId,
            "lobby_updated",
            Map.of("sessionId", sessionId, "playerCount", remaining, "status", session.getStatus())
        );
        return snapshot(sessionId, userId);
    }

    public SessionSnapshotResponse selectFormation(UUID sessionId, UUID userId, Formation formation) {
        GameSession session = gameSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        if (session.getStatus() != SessionStatus.FORMATION_SELECT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is not in formation selection phase");
        }

        SessionPlayer player = sessionPlayerRepository.findBySession_IdAndUserId(sessionId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not in session"));

        player.setFormation(formation);
        sseEmitterRegistry.broadcast(
            sessionId,
            "formation_chosen",
            Map.of("sessionId", sessionId, "userId", userId, "formation", formation)
        );

        if (sessionPlayerRepository.countBySession_Id(sessionId) == 2) {
            session.moveToAuctioning();
            sseEmitterRegistry.broadcast(
                sessionId,
                "auctioning_started",
                Map.of("sessionId", sessionId)
            );
        }

        return snapshot(sessionId, userId);
    }

    public SessionSnapshotResponse getSnapshot(UUID sessionId, UUID userId) {
        gameSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        return snapshot(sessionId, userId);
    }

    public void advanceTimedPhases() {
        Instant now = Instant.now();

        List<GameSession> countdownDone = gameSessionRepository.findByStatusAndCountdownEndsAtLessThanEqual(
            SessionStatus.COUNTDOWN,
            now
        );
        for (GameSession session : countdownDone) {
            if (sessionPlayerRepository.countBySession_Id(session.getId()) == 2) {
                session.moveToFormationSelect(now.plus(30, ChronoUnit.SECONDS));
                sseEmitterRegistry.broadcast(
                    session.getId(),
                    "formation_phase_started",
                    Map.of("sessionId", session.getId(), "endsAt", session.getCountdownEndsAt())
                );
            } else {
                session.moveToWaiting();
            }
        }

        List<GameSession> formationDone = gameSessionRepository.findByStatusAndCountdownEndsAtLessThanEqual(
            SessionStatus.FORMATION_SELECT,
            now
        );
        for (GameSession session : formationDone) {
            if (sessionPlayerRepository.countBySession_Id(session.getId()) == 2) {
                session.moveToAuctioning();
                sseEmitterRegistry.broadcast(
                    session.getId(),
                    "auctioning_started",
                    Map.of("sessionId", session.getId())
                );
            } else {
                session.moveToWaiting();
            }
        }
    }

    private SessionSnapshotResponse snapshot(UUID sessionId, UUID userId) {
        GameSession session = gameSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        long playerCount = sessionPlayerRepository.countBySession_Id(sessionId);
        SessionPlayer me = sessionPlayerRepository.findBySession_IdAndUserId(sessionId, userId).orElse(null);

        return new SessionSnapshotResponse(
            session.getId(),
            session.getStatus(),
            session.getCountdownEndsAt(),
            Math.toIntExact(playerCount),
            me != null,
            me != null ? me.getFormation() : null
        );
    }
}
