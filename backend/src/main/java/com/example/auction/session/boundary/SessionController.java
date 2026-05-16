package com.example.auction.session.boundary;

import com.example.auction.bidding.control.AuctionService;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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

    public SessionController(
        LobbyService lobbyService,
        SessionAdminService sessionAdminService,
        AuctionService auctionService,
        ActiveSessionStore sessionStore,
        MatchHistoryRepository matchHistoryRepository,
        MatchResultRecoveryService matchResultRecoveryService,
        SseEmitterRegistry sseEmitterRegistry,
        @Qualifier("redisObjectMapper") ObjectMapper redisObjectMapper
    ) {
        this.lobbyService = lobbyService;
        this.sessionAdminService = sessionAdminService;
        this.auctionService = auctionService;
        this.sessionStore = sessionStore;
        this.matchHistoryRepository = matchHistoryRepository;
        this.matchResultRecoveryService = matchResultRecoveryService;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.redisObjectMapper = redisObjectMapper;
    }

    @GetMapping("/{sessionId}")
    public SessionSnapshotResponse getSessionSnapshot(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        return lobbyService.getSnapshot(sessionId, extractUserId(authorization));
    }

    @PostMapping("/{sessionId}/formation")
    public SessionSnapshotResponse selectFormation(
        @PathVariable UUID sessionId,
        @Valid @RequestBody SelectFormationRequest request,
        @RequestHeader("Authorization") String authorization
    ) {
        return lobbyService.selectFormation(sessionId, extractUserId(authorization), request.formation());
    }

    @PostMapping("/{sessionId}/confirm-formation-ready")
    public SessionSnapshotResponse confirmFormationReady(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        return lobbyService.confirmFormationReady(sessionId, extractUserId(authorization));
    }

    @PostMapping("/{sessionId}/confirm-match-start")
    public SessionSnapshotResponse confirmMatchStart(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = extractUserId(authorization);
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
            snap.homeNickname(), snap.awayNickname(),
            snap.homeBudget(), snap.awayBudget(),
            snap.homeScore(), snap.awayScore()
        );
    }

    @GetMapping("/{sessionId}/match-result")
    public ResponseEntity<MatchResultResponse> getMatchResult(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = extractUserId(authorization);

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
                AuctionService.TeamSnapshot teams = auctionService.buildTeamSnapshot(session, userId);
                return ResponseEntity.ok(new MatchResultResponse(
                    myScore, oppScore,
                    teams.myTeam(), teams.opponentTeam(),
                    teams.myFormation(), teams.opponentFormation()
                ));
            }
        }

        // Fallback to archived match_history (session already removed from Redis)
        return matchHistoryRepository.findBySessionId(sessionId)
            .map(h -> buildResultFromArchive(h, userId))
            .orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<MatchResultResponse> buildResultFromArchive(MatchHistory h, UUID userId) {
        boolean isHome = h.getHomeUserId().equals(userId);
        int myScore = isHome ? h.getHomeScore() : h.getAwayScore();
        int oppScore = isHome ? h.getAwayScore() : h.getHomeScore();

        try {
            ActiveSession archived = redisObjectMapper.readValue(h.getReplayData(), ActiveSession.class);
            AuctionService.TeamSnapshot teams = auctionService.buildTeamSnapshot(archived, userId);
            return ResponseEntity.ok(new MatchResultResponse(
                myScore, oppScore,
                teams.myTeam(), teams.opponentTeam(),
                teams.myFormation(), teams.opponentFormation()
            ));
        } catch (Exception e) {
            log.warn("Failed to deserialize replay_data for session {}, returning scores only", h.getSessionId(), e);
            return ResponseEntity.ok(new MatchResultResponse(
                myScore, oppScore,
                List.of(), List.of(),
                isHome ? h.getHomeFormation() : h.getAwayFormation(),
                isHome ? h.getAwayFormation() : h.getHomeFormation()
            ));
        }
    }

    @PostMapping("/{sessionId}/ping")
    public ResponseEntity<Void> ping(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = extractUserId(authorization);
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
        UUID userId = extractUserId(authorization);

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
    public void deleteSession(@PathVariable UUID sessionId) {
        sessionAdminService.deleteSession(sessionId);
    }

    @DeleteMapping
    public Map<String, Integer> deleteActiveSessions() {
        return sessionAdminService.deleteActiveSessions();
    }

    private UUID extractUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Authorization: Bearer <uuid>");
        }
        try {
            return UUID.fromString(authorization.substring("Bearer ".length()).trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bearer token format");
        }
    }
}
