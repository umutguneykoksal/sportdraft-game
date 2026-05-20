package com.example.auction.session.boundary;

import com.example.auction.bidding.control.AuctionService;
import com.example.auction.common.web.AdminApiKeyGuard;
import com.example.auction.common.web.AuthTokenResolver;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.lobby.boundary.SessionSnapshotResponse;
import com.example.auction.lobby.control.LobbyService;
import com.example.auction.session.control.SessionAdminService;
import com.example.auction.session.entity.MatchHistory;
import com.example.auction.session.entity.MatchHistoryRepository;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.simulation.boundary.MatchResultResponse;
import com.example.auction.simulation.control.MatchResultRecoveryService;
import com.example.auction.simulation.entity.MatchEvent;
import com.example.auction.simulation.entity.MatchEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private final LobbyService lobbyService;
    private final SessionAdminService sessionAdminService;
    private final AuctionService auctionService;
    private final ActiveSessionStore sessionStore;
    private final MatchHistoryRepository matchHistoryRepository;
    private final MatchResultRecoveryService matchResultRecoveryService;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final ObjectMapper redisObjectMapper;
    private final AdminApiKeyGuard adminApiKeyGuard;
    private final AuthTokenResolver authTokenResolver;

    public SessionController(
        LobbyService lobbyService,
        SessionAdminService sessionAdminService,
        AuctionService auctionService,
        ActiveSessionStore sessionStore,
        MatchHistoryRepository matchHistoryRepository,
        MatchResultRecoveryService matchResultRecoveryService,
        SseEmitterRegistry sseEmitterRegistry,
        AdminApiKeyGuard adminApiKeyGuard,
        AuthTokenResolver authTokenResolver,
        @Qualifier("redisObjectMapper") ObjectMapper redisObjectMapper
    ) {
        this.lobbyService = lobbyService;
        this.sessionAdminService = sessionAdminService;
        this.auctionService = auctionService;
        this.sessionStore = sessionStore;
        this.matchHistoryRepository = matchHistoryRepository;
        this.matchResultRecoveryService = matchResultRecoveryService;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.adminApiKeyGuard = adminApiKeyGuard;
        this.authTokenResolver = authTokenResolver;
        this.redisObjectMapper = redisObjectMapper;
    }

    @GetMapping("/{sessionId}")
    public SessionSnapshotResponse getSessionSnapshot(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        return lobbyService.getSnapshot(sessionId, authTokenResolver.resolveUserId(authorization));
    }

    @PostMapping("/{sessionId}/formation")
    public SessionSnapshotResponse selectFormation(
        @PathVariable UUID sessionId,
        @Valid @RequestBody SelectFormationRequest request,
        @RequestHeader("Authorization") String authorization
    ) {
        return lobbyService.selectFormation(sessionId, authTokenResolver.resolveUserId(authorization), request.formation());
    }

    @PostMapping("/{sessionId}/confirm-formation-ready")
    public SessionSnapshotResponse confirmFormationReady(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        return lobbyService.confirmFormationReady(sessionId, authTokenResolver.resolveUserId(authorization));
    }

    @PostMapping("/{sessionId}/confirm-match-start")
    public SessionSnapshotResponse confirmMatchStart(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = authTokenResolver.resolveUserId(authorization);
        auctionService.confirmMatchStart(sessionId, userId);
        return lobbyService.getSnapshot(sessionId, userId);
    }

    @GetMapping("/{sessionId}/spectate")
    public SpectateStateResponse spectate(@PathVariable UUID sessionId) {
        ActiveSession session = sessionStore.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        var snap = auctionService.buildSpectateSnapshot(session);
        return new SpectateStateResponse(
            snap.status(), snap.currentRound(),
            snap.homeTeam(), snap.awayTeam(),
            snap.homeFormation(), snap.awayFormation(),
            snap.homeUserId(), snap.awayUserId(),
            snap.homeNickname(), snap.awayNickname(),
            snap.homeBudget(), snap.awayBudget(),
            snap.homeScore(), snap.awayScore()
        );
    }

    @GetMapping("/{sessionId}/spectate/match-timeline")
    public ResponseEntity<List<MatchEvent>> getSpectateMatchTimeline(@PathVariable UUID sessionId) {
        ActiveSession session = sessionStore.findById(sessionId).orElse(null);
        if (session != null) {
            matchResultRecoveryService.recoverIfMissing(sessionId);
            session = sessionStore.findById(sessionId).orElse(null);
            if (session != null) {
                List<MatchEvent> timeline = session.getMatchTimeline();
                if (timeline != null && !timeline.isEmpty()) {
                    return ResponseEntity.ok(timeline);
                }
            }
        }

        return matchHistoryRepository.findBySessionId(sessionId)
            .map(this::extractTimelineFromArchiveForSpectate)
            .orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<List<MatchEvent>> extractTimelineFromArchiveForSpectate(MatchHistory h) {
        try {
            ActiveSession archived = redisObjectMapper.readValue(h.getReplayData(), ActiveSession.class);
            List<MatchEvent> timeline = archived.getMatchTimeline();
            if (timeline != null && !timeline.isEmpty()) {
                return ResponseEntity.ok(timeline);
            }
            return ResponseEntity.ok(List.of());
        } catch (Exception e) {
            log.warn("Failed to deserialize replay_data for spectate timeline, session {}", h.getSessionId(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/{sessionId}/match-result")
    public ResponseEntity<MatchResultResponse> getMatchResult(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = authTokenResolver.resolveUserId(authorization);

        // Try active Redis session first
        ActiveSession session = sessionStore.findById(sessionId).orElse(null);
        if (session != null) {
            if (!session.hasPlayerWithUserId(userId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found in session");
            }
            matchResultRecoveryService.recoverIfMissing(sessionId);
            session = sessionStore.findById(sessionId).orElse(null);
            if (session != null && session.getHomeScore() != null) {
                List<ActivePlayer> sorted = session.playersSortedById();
                boolean isHome = !sorted.isEmpty() && sorted.get(0).getUserId().equals(userId);
                int myScore = isHome ? session.getHomeScore() : session.getAwayScore();
                int oppScore = isHome ? session.getAwayScore() : session.getHomeScore();
                String homeNick = sorted.size() > 0 ? sorted.get(0).getNickname() : "Home";
                String awayNick = sorted.size() > 1 ? sorted.get(1).getNickname() : "Away";
                String myNickname = isHome ? homeNick : awayNick;
                String oppNickname = isHome ? awayNick : homeNick;
                UUID homeUserId = sorted.isEmpty() ? userId : sorted.get(0).getUserId();
                UUID awayUserId = sorted.size() > 1 ? sorted.get(1).getUserId() : userId;
                AuctionService.TeamSnapshot teams = auctionService.buildTeamSnapshot(session, userId);
                
                // Calculate stats from timeline
                List<MatchEvent> timeline = session.getMatchTimeline();
                int[] stats = calculateStats(timeline, userId);
                
                return ResponseEntity.ok(new MatchResultResponse(
                    myScore, oppScore,
                    teams.myTeam(), teams.opponentTeam(),
                    teams.myFormation(), teams.opponentFormation(),
                    myNickname, oppNickname,
                    userId.toString(),
                    stats[0], stats[1], stats[2], stats[3],
                    homeUserId.toString(), awayUserId.toString(),
                    homeNick, awayNick,
                    session.getHomeScore(), session.getAwayScore()
                ));
            }
        }

        // Fallback to archived match_history (session already removed from Redis)
        return matchHistoryRepository.findBySessionId(sessionId)
            .map(h -> buildResultFromArchive(h, userId))
            .orElse(ResponseEntity.notFound().build());
    }
    
    private int[] calculateStats(List<MatchEvent> timeline, UUID userId) {
        if (timeline == null || timeline.isEmpty()) {
            return new int[]{0, 0, 0, 0};
        }
        
        Set<MatchEventType> shotTypes = Set.of(
            MatchEventType.GOAL, MatchEventType.SHOT_SAVED, MatchEventType.SHOT_MISSED
        );
        
        int myShots = 0, oppShots = 0, myFouls = 0, oppFouls = 0;
        
        for (MatchEvent e : timeline) {
            boolean isMine = userId.equals(e.teamId());
            
            if (shotTypes.contains(e.type())) {
                if (isMine) myShots++; else oppShots++;
            }
            if (e.type() == MatchEventType.FOUL) {
                if (isMine) myFouls++; else oppFouls++;
            }
        }
        
        return new int[]{myShots, oppShots, myFouls, oppFouls};
    }

    private ResponseEntity<MatchResultResponse> buildResultFromArchive(MatchHistory h, UUID userId) {
        boolean isHome = h.getHomeUserId().equals(userId);
        int myScore = isHome ? h.getHomeScore() : h.getAwayScore();
        int oppScore = isHome ? h.getAwayScore() : h.getHomeScore();

        try {
            ActiveSession archived = redisObjectMapper.readValue(h.getReplayData(), ActiveSession.class);
            List<ActivePlayer> sorted = archived.playersSortedById();
            String homeNick = sorted.size() > 0 ? sorted.get(0).getNickname() : "Home";
            String awayNick = sorted.size() > 1 ? sorted.get(1).getNickname() : "Away";
            String myNickname = isHome ? homeNick : awayNick;
            String oppNickname = isHome ? awayNick : homeNick;
            AuctionService.TeamSnapshot teams = auctionService.buildTeamSnapshot(archived, userId);
            
            List<MatchEvent> timeline = archived.getMatchTimeline();
            int[] stats = calculateStats(timeline, userId);
            
            return ResponseEntity.ok(new MatchResultResponse(
                myScore, oppScore,
                teams.myTeam(), teams.opponentTeam(),
                teams.myFormation(), teams.opponentFormation(),
                myNickname, oppNickname,
                userId.toString(),
                stats[0], stats[1], stats[2], stats[3],
                h.getHomeUserId().toString(), h.getAwayUserId().toString(),
                homeNick, awayNick,
                h.getHomeScore(), h.getAwayScore()
            ));
        } catch (Exception e) {
            log.warn("Failed to deserialize replay_data for session {}, returning scores only", h.getSessionId(), e);
            return ResponseEntity.ok(new MatchResultResponse(
                myScore, oppScore,
                List.of(), List.of(),
                isHome ? h.getHomeFormation() : h.getAwayFormation(),
                isHome ? h.getAwayFormation() : h.getHomeFormation(),
                null, null,
                userId.toString(),
                0, 0, 0, 0,
                h.getHomeUserId().toString(), h.getAwayUserId().toString(),
                "Home", "Away",
                h.getHomeScore(), h.getAwayScore()
            ));
        }
    }

    @PostMapping("/{sessionId}/ping")
    public ResponseEntity<Void> ping(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = authTokenResolver.resolveUserId(authorization);
        sseEmitterRegistry.recordPlayerPing(sessionId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the match event timeline for 2D replay visualization.
     */
    @GetMapping("/{sessionId}/match-timeline")
    public ResponseEntity<List<MatchEvent>> getMatchTimeline(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = authTokenResolver.resolveUserId(authorization);

        // Try active Redis session first
        ActiveSession session = sessionStore.findById(sessionId).orElse(null);
        if (session != null) {
            if (!session.hasPlayerWithUserId(userId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found in session");
            }
            List<MatchEvent> timeline = session.getMatchTimeline();
            if (timeline != null && !timeline.isEmpty()) {
                return ResponseEntity.ok(timeline);
            }
        }

        // Fallback to archived match_history
        return matchHistoryRepository.findBySessionId(sessionId)
            .map(h -> extractTimelineFromArchive(h, userId))
            .orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<List<MatchEvent>> extractTimelineFromArchive(MatchHistory h, UUID userId) {
        if (!h.getHomeUserId().equals(userId) && !h.getAwayUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found in session");
        }
        try {
            ActiveSession archived = redisObjectMapper.readValue(h.getReplayData(), ActiveSession.class);
            List<MatchEvent> timeline = archived.getMatchTimeline();
            if (timeline != null && !timeline.isEmpty()) {
                return ResponseEntity.ok(timeline);
            }
            return ResponseEntity.ok(List.of());
        } catch (Exception e) {
            log.warn("Failed to deserialize replay_data for timeline, session {}", h.getSessionId(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @DeleteMapping("/{sessionId}")
    public void deleteSession(
        @PathVariable UUID sessionId,
        @RequestHeader(value = AdminApiKeyGuard.HEADER_NAME, required = false) String adminKey
    ) {
        adminApiKeyGuard.requireAdmin(adminKey);
        sessionAdminService.deleteSession(sessionId);
    }

    @DeleteMapping
    public Map<String, Integer> deleteActiveSessions(
        @RequestHeader(value = AdminApiKeyGuard.HEADER_NAME, required = false) String adminKey
    ) {
        adminApiKeyGuard.requireAdmin(adminKey);
        return sessionAdminService.deleteActiveSessions();
    }
}
