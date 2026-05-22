package dev.sportdraft.session.boundary;

import dev.sportdraft.bidding.control.AuctionService;
import dev.sportdraft.common.web.AdminApiKeyGuard;
import dev.sportdraft.common.web.AuthTokenResolver;
import dev.sportdraft.lobby.boundary.SessionSnapshotResponse;
import dev.sportdraft.lobby.control.LobbyService;
import dev.sportdraft.session.control.SessionAdminService;
import dev.sportdraft.session.control.SessionReadService;
import dev.sportdraft.simulation.boundary.MatchResultResponse;
import dev.sportdraft.simulation.entity.MatchEvent;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * @deprecated Use {@code /api/v2/sessions} for player-facing endpoints. Retained as operational fallback.
 */
@Deprecated
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final LobbyService lobbyService;
    private final SessionAdminService sessionAdminService;
    private final AuctionService auctionService;
    private final SessionReadService sessionReadService;
    private final AdminApiKeyGuard adminApiKeyGuard;
    private final AuthTokenResolver authTokenResolver;

    public SessionController(
        LobbyService lobbyService,
        SessionAdminService sessionAdminService,
        AuctionService auctionService,
        SessionReadService sessionReadService,
        AdminApiKeyGuard adminApiKeyGuard,
        AuthTokenResolver authTokenResolver
    ) {
        this.lobbyService = lobbyService;
        this.sessionAdminService = sessionAdminService;
        this.auctionService = auctionService;
        this.sessionReadService = sessionReadService;
        this.adminApiKeyGuard = adminApiKeyGuard;
        this.authTokenResolver = authTokenResolver;
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
        return sessionReadService.spectate(sessionId);
    }

    @GetMapping("/{sessionId}/spectate/match-timeline")
    public ResponseEntity<List<MatchEvent>> getSpectateMatchTimeline(@PathVariable UUID sessionId) {
        return sessionReadService.getSpectateMatchTimeline(sessionId);
    }

    @GetMapping("/{sessionId}/match-result")
    public ResponseEntity<MatchResultResponse> getMatchResult(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = authTokenResolver.resolveUserId(authorization);
        return sessionReadService.getMatchResult(sessionId, userId);
    }

    @PostMapping("/{sessionId}/ping")
    public ResponseEntity<Void> ping(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = authTokenResolver.resolveUserId(authorization);
        sessionReadService.recordPing(sessionId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{sessionId}/match-timeline")
    public ResponseEntity<List<MatchEvent>> getMatchTimeline(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = authTokenResolver.resolveUserId(authorization);
        return sessionReadService.getMatchTimeline(sessionId, userId);
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
