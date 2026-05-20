package com.example.auction.lobby.control;

import com.example.auction.bidding.control.AuctionService;
import com.example.auction.bidding.control.CardCatalogService;
import com.example.auction.common.realtime.SessionEventLog;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.lobby.boundary.LobbyRoomResponse;
import com.example.auction.lobby.boundary.SessionSnapshotResponse;
import com.example.auction.lobby.boundary.LobbyPlayerView;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.PoolLeague;
import com.example.auction.session.entity.PoolMinRating;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.control.SessionAccess;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LobbyService {

    private static final long STARTING_BUDGET_CENTS = 100L;
    private static final int MAX_CONCURRENT_ROOMS = 4;
    /** Countdown after both players confirm formation, before auction begins. */
    private static final long FORMATION_BOTH_READY_COUNTDOWN_SECONDS = 5L;

    private final ActiveSessionStore sessionStore;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final CardCatalogService cardCatalog;
    private final SessionEventLog eventLog;
    private final AuctionService auctionService;

    public LobbyService(ActiveSessionStore sessionStore, SseEmitterRegistry sseEmitterRegistry,
                         CardCatalogService cardCatalog, SessionEventLog eventLog,
                         @Lazy AuctionService auctionService) {
        this.sessionStore = sessionStore;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.cardCatalog = cardCatalog;
        this.eventLog = eventLog;
        this.auctionService = auctionService;
    }

    public List<LobbyRoomResponse> listRooms() {
        List<UUID> activeIds = sessionStore.findAllActiveIds();
        if (activeIds.isEmpty()) return List.of();
        List<ActiveSession> sessions = sessionStore.findByStatusNot(SessionStatus.FINISHED);
        return sessions.stream()
            .map(s -> new LobbyRoomResponse(
                s.getId(),
                s.getStatus().name(),
                s.playerCount(),
                s.getMaxPlayers(),
                s.getGameMode(),
                s.getMinPoolRating(),
                s.getPoolLeague(),
                s.getPlayers().stream().map(ActivePlayer::getNickname).toList(),
                s.getCreatedAt(),
                sseEmitterRegistry.spectatorCount(s.getId())
            ))
            .toList();
    }

    public SessionSnapshotResponse createRoom(UUID userId, String nickname, GameMode gameMode) {
        return createRoom(userId, nickname, gameMode, PoolMinRating.DEFAULT);
    }

    public SessionSnapshotResponse createRoom(UUID userId, String nickname, GameMode gameMode, Integer minPoolRating) {
        return createRoom(userId, nickname, gameMode, minPoolRating, null);
    }

    public SessionSnapshotResponse createRoom(
        UUID userId,
        String nickname,
        GameMode gameMode,
        Integer minPoolRating,
        String poolLeague
    ) {
        if (sessionStore.findAllActiveIds().size() >= MAX_CONCURRENT_ROOMS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Maximum %d concurrent rooms reached. Try again later.".formatted(MAX_CONCURRENT_ROOMS));
        }
        GameMode mode = gameMode != null ? gameMode : GameMode.DUEL;
        int minRating = PoolMinRating.resolve(minPoolRating);
        PoolLeague league = PoolLeague.resolve(poolLeague);
        ensurePoolHasCards(league, minRating);

        ActiveSession session = new ActiveSession(UUID.randomUUID(), SessionStatus.WAITING, Instant.now(), mode);
        session.setMinPoolRating(minRating);
        session.setPoolLeague(league);
        String safeName = sanitizeNickname(nickname, userId);
        ActivePlayer player = new ActivePlayer(
            UUID.randomUUID(), userId, safeName, Formation.FOUR_FOUR_TWO, STARTING_BUDGET_CENTS
        );
        session.getPlayers().add(player);
        session.addToTurnOrder(userId);
        sessionStore.save(session);
        return snapshot(session, userId);
    }

    public SessionSnapshotResponse createRoom(UUID userId, String nickname) {
        return createRoom(userId, nickname, GameMode.DUEL);
    }

    public SessionSnapshotResponse joinRoom(UUID sessionId, UUID userId, String nickname) {
        ActiveSession session = sessionStore.getById(sessionId);
        if (session.getStatus() != SessionStatus.WAITING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is no longer accepting players");
        }
        if (session.hasPlayerWithUserId(userId)) {
            return snapshot(session, userId);
        }
        if (session.playerCount() >= session.getMaxPlayers()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is full");
        }

        long versionBefore = session.getVersion();
        String safeName = sanitizeNickname(nickname, userId);
        ActivePlayer player = new ActivePlayer(
            UUID.randomUUID(), userId, safeName, Formation.FOUR_FOUR_TWO, STARTING_BUDGET_CENTS
        );
        session.getPlayers().add(player);
        session.addToTurnOrder(userId);

        if (session.playerCount() == session.getMaxPlayers()) {
            session.moveToCountdown(Instant.now().plus(5, ChronoUnit.SECONDS));
            sessionStore.saveIfVersionMatch(session, versionBefore);
            sseEmitterRegistry.broadcast(
                sessionId, "countdown_started",
                Map.of("sessionId", sessionId, "endsAt", session.getCountdownEndsAt())
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

    /**
     * @deprecated Use {@link #createRoom} + {@link #joinRoom} instead.
     */
    @Deprecated
    public SessionSnapshotResponse joinLobby(UUID userId, String nickname) {
        List<ActiveSession> waitingSessions = sessionStore.findWaitingSessions();
        ActiveSession target = waitingSessions.stream()
            .filter(s -> s.playerCount() < s.getMaxPlayers())
            .findFirst()
            .orElse(null);
        if (target != null) {
            return joinRoom(target.getId(), userId, nickname);
        }
        return createRoom(userId, nickname);
    }

    private String sanitizeNickname(String nickname, UUID userId) {
        if (nickname != null && !nickname.isBlank()) {
            return nickname.strip().substring(0, Math.min(nickname.strip().length(), 20));
        }
        return "Player-" + userId.toString().substring(0, 6);
    }

    public SessionSnapshotResponse leaveLobby(UUID sessionId, UUID userId) {
        ActiveSession session = sessionStore.getById(sessionId);
        long versionBefore = session.getVersion();

        SessionAccess.requireMember(session, userId);
        ActivePlayer player = session.findPlayerByUserId(userId).orElseThrow();

        session.getPlayers().remove(player);

        // Empty room — delete instead of leaving a zombie session in Redis
        if (session.playerCount() == 0) {
            sessionStore.delete(sessionId);
            sseEmitterRegistry.forgetSession(sessionId);
            return snapshot(session, userId);
        }

        boolean needsReset = session.playerCount() < session.getMaxPlayers()
            && (session.getStatus() == SessionStatus.COUNTDOWN
                || session.getStatus() == SessionStatus.FORMATION_SELECT);
        if (needsReset) {
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

        SessionAccess.requireMember(session, userId);
        ActivePlayer player = session.findPlayerByUserId(userId).orElseThrow();

        player.selectFormation(formation);
        sessionStore.saveIfVersionMatch(session, versionBefore);

        sseEmitterRegistry.broadcast(
            sessionId, "formation_chosen",
            Map.of("sessionId", sessionId, "userId", userId, "formation", formation)
        );

        return snapshot(session, userId);
    }

    public SessionSnapshotResponse confirmFormationReady(UUID sessionId, UUID userId) {
        ActiveSession session = sessionStore.getById(sessionId);
        long versionBefore = session.getVersion();

        if (session.getStatus() != SessionStatus.FORMATION_SELECT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not in formation selection phase");
        }
        SessionAccess.requireMember(session, userId);

        session.getPlayers().forEach(ActivePlayer::confirmDefaultFormationIfMissing);
        boolean changed = session.registerMatchStartReady(userId);
        if (!changed) {
            return snapshot(session, userId);
        }

        if (session.areAllMatchStartReady()) {
            session.setCountdownEndsAt(
                Instant.now().plus(FORMATION_BOTH_READY_COUNTDOWN_SECONDS, ChronoUnit.SECONDS)
            );
            sessionStore.saveIfVersionMatch(session, versionBefore);
            sseEmitterRegistry.broadcast(
                sessionId, "formation_ready",
                Map.of("sessionId", sessionId, "readyCount", session.matchStartReadyCount(),
                       "countdownEndsAt", session.getCountdownEndsAt())
            );
        } else {
            session.setCountdownEndsAt(null);
            sessionStore.saveIfVersionMatch(session, versionBefore);
            sseEmitterRegistry.broadcast(
                sessionId, "formation_ready",
                Map.of("sessionId", sessionId, "readyCount", session.matchStartReadyCount())
            );
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
                if (session.playerCount() == session.getMaxPlayers()) {
                    session.moveToFormationSelect(null);
                    sessionStore.saveIfVersionMatch(session, vBefore);
                    sseEmitterRegistry.broadcast(
                        session.getId(), "formation_phase_started",
                        Map.of("sessionId", session.getId())
                    );
                } else {
                    session.moveToWaiting();
                    sessionStore.saveIfVersionMatch(session, vBefore);
                }
            }
        }

        for (ActiveSession session : sessionStore.findByStatus(SessionStatus.FORMATION_SELECT)) {
            if (session.getCountdownEndsAt() != null && !session.getCountdownEndsAt().isAfter(now)
                && session.areAllMatchStartReady()) {
                long vBefore = session.getVersion();
                if (session.playerCount() == session.getMaxPlayers()) {
                    session.getPlayers().forEach(ActivePlayer::confirmDefaultFormationIfMissing);
                    session.moveToAuctioning(buildShuffledCardPool(session));
                    sessionStore.saveIfVersionMatch(session, vBefore);
                    sseEmitterRegistry.broadcast(
                        session.getId(), "auctioning_started",
                        Map.of("sessionId", session.getId())
                    );
                    auctionService.ensureOpenRound(session.getId());
                } else {
                    session.moveToWaiting();
                    sessionStore.saveIfVersionMatch(session, vBefore);
                }
            }
        }
    }

    private List<UUID> buildShuffledCardPool(ActiveSession session) {
        List<UUID> pool = new ArrayList<>(
            cardCatalog.getCardIdsForPool(session.getPoolLeague(), session.getMinPoolRating())
        );
        Collections.shuffle(pool);
        return pool;
    }

    private void ensurePoolHasCards(PoolLeague league, int minRating) {
        if (cardCatalog.getCardIdsForPool(league, minRating).isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "No cards available for %s at minimum rating %d".formatted(league.displayName(), minRating)
            );
        }
    }

    private SessionSnapshotResponse snapshot(ActiveSession session, UUID userId) {
        ActivePlayer me = session.findPlayerByUserId(userId).orElse(null);

        Instant matchStartDeadlineAt = null;
        int matchStartReadyCount = 0;
        boolean matchStartConfirmedByMe = false;
        if (session.getStatus() == SessionStatus.MATCH_START_PENDING
            || session.getStatus() == SessionStatus.FORMATION_SELECT) {
            matchStartDeadlineAt = session.getStatus() == SessionStatus.MATCH_START_PENDING
                ? session.getMatchStartDeadlineAt()
                : session.getCountdownEndsAt();
            matchStartReadyCount = session.matchStartReadyCount();
            matchStartConfirmedByMe = session.isMatchStartReady(userId);
        }

        List<LobbyPlayerView> players = new ArrayList<>();
        session.ensureTurnOrder();
        for (int i = 0; i < session.getTurnOrder().size(); i++) {
            UUID uid = session.getTurnOrder().get(i);
            int seat = i;
            session.findPlayerByUserId(uid).ifPresent(p ->
                players.add(new LobbyPlayerView(p.getUserId(), p.getNickname(), seat))
            );
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
            matchStartConfirmedByMe,
            session.getVersion(),
            eventLog.currentSeq(session.getId()),
            session.getGameMode(),
            session.getMaxPlayers(),
            session.getMinPoolRating(),
            session.getPoolLeague(),
            players
        );
    }
}
