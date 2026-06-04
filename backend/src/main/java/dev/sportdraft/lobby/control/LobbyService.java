package dev.sportdraft.lobby.control;

import dev.sportdraft.bidding.control.AuctionService;
import dev.sportdraft.bidding.control.CardCatalogService;
import dev.sportdraft.common.realtime.SessionEventLog;
import dev.sportdraft.common.sse.SseEmitterRegistry;
import dev.sportdraft.lobby.entity.LobbyCountdownPhase;
import dev.sportdraft.lobby.boundary.LobbyRoomResponse;
import dev.sportdraft.lobby.boundary.PoolMinRatingOptionView;
import dev.sportdraft.lobby.boundary.PoolMinRatingOptionsResponse;
import dev.sportdraft.lobby.boundary.SessionSnapshotResponse;
import dev.sportdraft.lobby.boundary.LobbyPlayerView;
import dev.sportdraft.session.entity.CardPoolRules;
import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.entity.PoolLeague;
import dev.sportdraft.session.entity.PoolMinRating;
import dev.sportdraft.session.control.SessionDisconnectPolicy;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActivePlayer;
import dev.sportdraft.session.control.SessionAccess;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import dev.sportdraft.session.redis.SessionLock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LobbyService {

    private static final Logger log = LoggerFactory.getLogger(LobbyService.class);
    private static final long STARTING_BUDGET_CENTS = 100L;
    private static final int MAX_CONCURRENT_ROOMS = 4;
    /** Pre-draft lobby and in-progress auction only; post-draft competition lives in Postgres. */
    static final Set<SessionStatus> LOBBY_LISTABLE_STATUSES = Set.of(
        SessionStatus.WAITING,
        SessionStatus.COUNTDOWN,
        SessionStatus.FORMATION_SELECT,
        SessionStatus.AUCTIONING,
        SessionStatus.MATCH_START_PENDING
    );
    /** Opponent-found lobby countdown before formation pick. */
    private static final long LOBBY_PAIRING_COUNTDOWN_SECONDS = 5L;
    /** Countdown after first player confirms ready; auction starts when it expires. */
    private static final long FORMATION_FIRST_READY_COUNTDOWN_SECONDS = 10L;

    private final ActiveSessionStore sessionStore;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final CardCatalogService cardCatalog;
    private final SessionEventLog eventLog;
    private final AuctionService auctionService;
    private final SessionLock sessionLock;

    public LobbyService(ActiveSessionStore sessionStore, SseEmitterRegistry sseEmitterRegistry,
                         CardCatalogService cardCatalog, SessionEventLog eventLog,
                         @Lazy AuctionService auctionService,
                         SessionLock sessionLock) {
        this.sessionStore = sessionStore;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.cardCatalog = cardCatalog;
        this.eventLog = eventLog;
        this.auctionService = auctionService;
        this.sessionLock = sessionLock;
    }

    public PoolMinRatingOptionsResponse getPoolMinRatingOptions(String poolLeague) {
        PoolLeague league = PoolLeague.fromWire(poolLeague);
        List<PoolMinRatingOptionView> options = PoolMinRating.ALLOWED.stream()
            .map(minRating -> {
                int cardCount = cardCatalog.countCardsForPool(league, minRating);
                return new PoolMinRatingOptionView(minRating, cardCount, CardPoolRules.isViable(cardCount));
            })
            .toList();
        return new PoolMinRatingOptionsResponse(league.name(), options);
    }

    public List<LobbyRoomResponse> listRooms() {
        List<UUID> activeIds = sessionStore.findAllActiveIds();
        if (activeIds.isEmpty()) return List.of();
        List<ActiveSession> sessions = sessionStore.findByStatusNot(SessionStatus.FINISHED);
        return sessions.stream()
            .filter(s -> LOBBY_LISTABLE_STATUSES.contains(s.getStatus()))
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
        log.info(
            "Room created session={} host={} mode={} league={} minRating={}",
            session.getId(), userId, mode, league, minRating
        );
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
            session.moveToCountdown(Instant.now().plus(LOBBY_PAIRING_COUNTDOWN_SECONDS, ChronoUnit.SECONDS));
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
        log.info(
            "Player joined session={} user={} players={}/{} status={}",
            sessionId, userId, session.playerCount(), session.getMaxPlayers(), session.getStatus()
        );
        return snapshot(session, userId);
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

        // Empty room — delete immediately when everyone exits the lobby
        if (session.playerCount() == 0) {
            sessionStore.delete(sessionId);
            sseEmitterRegistry.forgetSession(sessionId);
            eventLog.forget(sessionId);
            log.info("Room deleted session={} reason=last_player_left user={}", sessionId, userId);
            return snapshot(session, userId);
        }

        boolean needsReset = SessionDisconnectPolicy.shouldResetLobby(session);
        if (needsReset) {
            session.moveToWaiting();
            sessionStore.saveIfVersionMatch(session, versionBefore);
            log.info(
                "Lobby reset session={} user={} remaining={} status={}",
                sessionId, userId, session.playerCount(), session.getStatus()
            );
            sseEmitterRegistry.broadcast(
                sessionId, "countdown_cancelled",
                Map.of("sessionId", sessionId, "remainingPlayers", session.playerCount())
            );
        } else {
            sessionStore.saveIfVersionMatch(session, versionBefore);
        }

        log.info(
            "Player left session={} user={} remaining={} status={}",
            sessionId, userId, session.playerCount(), session.getStatus()
        );
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
            if (startAuctionFromFormation(session, sessionId, versionBefore)) {
                auctionService.openRoundIfNeeded(sessionId);
            }
            return snapshot(session, userId);
        }

        if (session.getCountdownEndsAt() == null) {
            session.setCountdownEndsAt(
                Instant.now().plus(FORMATION_FIRST_READY_COUNTDOWN_SECONDS, ChronoUnit.SECONDS)
            );
            sessionStore.saveIfVersionMatch(session, versionBefore);
            sseEmitterRegistry.broadcast(
                sessionId, "formation_ready",
                Map.of("sessionId", sessionId, "readyCount", session.matchStartReadyCount(),
                       "countdownEndsAt", session.getCountdownEndsAt())
            );
        } else {
            sessionStore.saveIfVersionMatch(session, versionBefore);
            sseEmitterRegistry.broadcast(
                sessionId, "formation_ready",
                Map.of("sessionId", sessionId, "readyCount", session.matchStartReadyCount(),
                       "countdownEndsAt", session.getCountdownEndsAt())
            );
        }

        return snapshot(session, userId);
    }

    private boolean startAuctionFromFormation(ActiveSession session, UUID sessionId, long versionBefore) {
        session.getPlayers().forEach(ActivePlayer::confirmDefaultFormationIfMissing);
        if (session.playerCount() != session.getMaxPlayers()) {
            session.moveToWaiting();
            sessionStore.saveIfVersionMatch(session, versionBefore);
            return false;
        }
        session.moveToAuctioning(buildShuffledCardPool(session));
        sessionStore.saveIfVersionMatch(session, versionBefore);
        log.info(
            "Auction started session={} players={} poolSize={}",
            sessionId, session.playerCount(), session.getCardPool().size()
        );
        sseEmitterRegistry.broadcast(
            sessionId, "auctioning_started",
            Map.of("sessionId", sessionId)
        );
        return true;
    }

    public SessionSnapshotResponse getSnapshot(UUID sessionId, UUID userId) {
        ActiveSession session = sessionStore.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        return snapshot(session, userId);
    }

    public void advanceTimedPhases() {
        Instant now = Instant.now();
        advanceCountdownPhases(now);
        advanceFormationPhases(now);
    }

    private void advanceCountdownPhases(Instant now) {
        for (ActiveSession snapshot : sessionStore.findByStatus(SessionStatus.COUNTDOWN)) {
            UUID sessionId = snapshot.getId();
            try {
                sessionLock.runVoid(sessionId, () -> {
                    ActiveSession session = sessionStore.getById(sessionId);
                    if (session.getStatus() != SessionStatus.COUNTDOWN) {
                        return;
                    }
                    if (session.getCountdownEndsAt() == null || session.getCountdownEndsAt().isAfter(now)) {
                        return;
                    }
                    long vBefore = session.getVersion();
                    if (session.playerCount() == session.getMaxPlayers()) {
                        session.moveToFormationSelect(null);
                        sessionStore.saveIfVersionMatch(session, vBefore);
                        log.info("Countdown finished session={} status=FORMATION_SELECT", sessionId);
                        sseEmitterRegistry.broadcast(
                            sessionId, "formation_phase_started",
                            Map.of("sessionId", sessionId)
                        );
                    } else {
                        session.moveToWaiting();
                        sessionStore.saveIfVersionMatch(session, vBefore);
                    }
                });
            } catch (ActiveSessionStore.OptimisticLockException ignored) {
                // Retry on next scheduler tick (same pattern as AuctionRoundScheduler).
            }
        }
    }

    private void advanceFormationPhases(Instant now) {
        List<UUID> auctionStartedSessionIds = new ArrayList<>();
        for (ActiveSession snapshot : sessionStore.findByStatus(SessionStatus.FORMATION_SELECT)) {
            UUID sessionId = snapshot.getId();
            try {
                boolean auctionStarted = Boolean.TRUE.equals(sessionLock.run(sessionId, () -> {
                    ActiveSession session = sessionStore.getById(sessionId);
                    if (session.getStatus() != SessionStatus.FORMATION_SELECT) {
                        return false;
                    }
                    if (session.getCountdownEndsAt() == null || session.getCountdownEndsAt().isAfter(now)) {
                        return false;
                    }
                    long vBefore = session.getVersion();
                    if (session.playerCount() == session.getMaxPlayers()) {
                        startAuctionFromFormation(session, sessionId, vBefore);
                        return session.getStatus() == SessionStatus.AUCTIONING;
                    }
                    session.moveToWaiting();
                    sessionStore.saveIfVersionMatch(session, vBefore);
                    return false;
                }));
                if (auctionStarted) {
                    auctionStartedSessionIds.add(sessionId);
                }
            } catch (ActiveSessionStore.OptimisticLockException ignored) {
                // Retry on next scheduler tick.
            }
        }
        for (UUID sessionId : auctionStartedSessionIds) {
            auctionService.openRoundIfNeeded(sessionId);
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
        int cardCount = cardCatalog.countCardsForPool(league, minRating);
        if (!CardPoolRules.isViable(cardCount)) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Not enough cards for %s at minimum rating %d (%d available, need %d)".formatted(
                    league.displayName(),
                    minRating,
                    cardCount,
                    CardPoolRules.MIN_CARD_COUNT
                )
            );
        }
    }

    private static LobbyCountdownPhase resolveCountdownPhase(ActiveSession session) {
        if (session.getStatus() == SessionStatus.COUNTDOWN && session.getCountdownEndsAt() != null) {
            return LobbyCountdownPhase.PAIRING;
        }
        if (session.getStatus() == SessionStatus.FORMATION_SELECT && session.getCountdownEndsAt() != null) {
            return LobbyCountdownPhase.FORMATION_AUCTION_START;
        }
        return null;
    }

    private Instant wireCountdownEndsAt(ActiveSession session, LobbyCountdownPhase phase) {
        Instant endsAt = session.getCountdownEndsAt();
        if (endsAt == null || phase == null) {
            return null;
        }
        Instant cap = switch (phase) {
            case PAIRING -> Instant.now().plus(LOBBY_PAIRING_COUNTDOWN_SECONDS, ChronoUnit.SECONDS);
            case FORMATION_AUCTION_START ->
                Instant.now().plus(FORMATION_FIRST_READY_COUNTDOWN_SECONDS, ChronoUnit.SECONDS);
        };
        if (endsAt.isAfter(cap)) {
            log.warn(
                "Normalized {} countdown session={} endsAt={} cap={}",
                phase, session.getId(), endsAt, cap
            );
            return cap;
        }
        return endsAt;
    }

    private SessionSnapshotResponse snapshot(ActiveSession session, UUID userId) {
        ActivePlayer me = session.findPlayerByUserId(userId).orElse(null);

        Instant matchStartDeadlineAt = null;
        int matchStartReadyCount = 0;
        boolean matchStartConfirmedByMe = false;
        if (session.getStatus() == SessionStatus.MATCH_START_PENDING) {
            matchStartDeadlineAt = session.getMatchStartDeadlineAt();
        }
        if (session.getStatus() == SessionStatus.MATCH_START_PENDING
            || session.getStatus() == SessionStatus.FORMATION_SELECT) {
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

        LobbyCountdownPhase countdownPhase = resolveCountdownPhase(session);
        Instant wireCountdownEndsAt = wireCountdownEndsAt(session, countdownPhase);

        return new SessionSnapshotResponse(
            session.getId(),
            session.getStatus(),
            countdownPhase,
            wireCountdownEndsAt,
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
