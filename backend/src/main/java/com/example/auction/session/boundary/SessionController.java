package com.example.auction.session.boundary;

import com.example.auction.bidding.control.AuctionService;
import com.example.auction.lobby.boundary.SessionSnapshotResponse;
import com.example.auction.lobby.control.LobbyService;
import com.example.auction.session.control.SessionAdminService;
import com.example.auction.session.entity.SessionPlayer;
import com.example.auction.session.entity.SessionPlayerRepository;
import com.example.auction.simulation.boundary.MatchResultResponse;
import com.example.auction.simulation.entity.MatchResultRepository;
import com.example.auction.simulation.control.MatchResultRecoveryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    private final LobbyService lobbyService;
    private final SessionAdminService sessionAdminService;
    private final AuctionService auctionService;
    private final MatchResultRepository matchResultRepository;
    private final SessionPlayerRepository sessionPlayerRepository;
    private final MatchResultRecoveryService matchResultRecoveryService;

    public SessionController(
        LobbyService lobbyService,
        SessionAdminService sessionAdminService,
        AuctionService auctionService,
        MatchResultRepository matchResultRepository,
        SessionPlayerRepository sessionPlayerRepository,
        MatchResultRecoveryService matchResultRecoveryService
    ) {
        this.lobbyService = lobbyService;
        this.sessionAdminService = sessionAdminService;
        this.auctionService = auctionService;
        this.matchResultRepository = matchResultRepository;
        this.sessionPlayerRepository = sessionPlayerRepository;
        this.matchResultRecoveryService = matchResultRecoveryService;
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

    @PostMapping("/{sessionId}/confirm-match-start")
    public SessionSnapshotResponse confirmMatchStart(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = extractUserId(authorization);
        auctionService.confirmMatchStart(sessionId, userId);
        return lobbyService.getSnapshot(sessionId, userId);
    }

    @GetMapping("/{sessionId}/match-result")
    public ResponseEntity<MatchResultResponse> getMatchResult(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = extractUserId(authorization);
        List<SessionPlayer> players = sessionPlayerRepository.findBySession_Id(sessionId);
        boolean inSession = players.stream().anyMatch(p -> p.getUserId().equals(userId));
        if (!inSession) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found in session");
        }
        matchResultRecoveryService.recoverIfMissing(sessionId);
        boolean isHome = players.size() >= 1 && players.get(0).getUserId().equals(userId);
        return matchResultRepository.findBySession_Id(sessionId)
            .map(m -> {
                int myScore = isHome ? m.getHomeScore() : m.getAwayScore();
                int oppScore = isHome ? m.getAwayScore() : m.getHomeScore();
                return ResponseEntity.ok(new MatchResultResponse(myScore, oppScore));
            })
            .orElse(ResponseEntity.notFound().build());
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
