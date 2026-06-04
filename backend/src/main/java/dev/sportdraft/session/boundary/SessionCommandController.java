package dev.sportdraft.session.boundary;

import dev.sportdraft.bidding.control.AuctionService;
import dev.sportdraft.common.web.AdminApiKeyGuard;
import dev.sportdraft.common.web.AuthTokenResolver;
import dev.sportdraft.session.control.SessionAdminService;
import dev.sportdraft.lobby.boundary.SessionSnapshotResponse;
import dev.sportdraft.lobby.control.LobbyService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2")
public class SessionCommandController {

    private final LobbyService lobbyService;
    private final AuctionService auctionService;
    private final AuthTokenResolver authTokenResolver;
    private final SessionAdminService sessionAdminService;
    private final AdminApiKeyGuard adminApiKeyGuard;

    public SessionCommandController(
        LobbyService lobbyService,
        AuctionService auctionService,
        AuthTokenResolver authTokenResolver,
        SessionAdminService sessionAdminService,
        AdminApiKeyGuard adminApiKeyGuard
    ) {
        this.lobbyService = lobbyService;
        this.auctionService = auctionService;
        this.authTokenResolver = authTokenResolver;
        this.sessionAdminService = sessionAdminService;
        this.adminApiKeyGuard = adminApiKeyGuard;
    }

    @DeleteMapping("/sessions/{sessionId}/players/me")
    public SessionSnapshotResponse leaveLobby(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        return lobbyService.leaveLobby(sessionId, authTokenResolver.resolveUserId(authorization));
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionSnapshotResponse getSnapshot(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        return lobbyService.getSnapshot(sessionId, authTokenResolver.resolveUserId(authorization));
    }

    @PostMapping("/sessions/{sessionId}/formation")
    public SessionSnapshotResponse selectFormation(
        @PathVariable UUID sessionId,
        @Valid @RequestBody SelectFormationRequest request,
        @RequestHeader("Authorization") String authorization
    ) {
        return lobbyService.selectFormation(
            sessionId,
            authTokenResolver.resolveUserId(authorization),
            request.formation()
        );
    }

    @PostMapping("/sessions/{sessionId}/confirm-formation-ready")
    public SessionSnapshotResponse confirmFormationReady(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        return lobbyService.confirmFormationReady(sessionId, authTokenResolver.resolveUserId(authorization));
    }

    @PostMapping("/sessions/{sessionId}/confirm-match-start")
    public SessionSnapshotResponse confirmMatchStart(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = authTokenResolver.resolveUserId(authorization);
        auctionService.confirmMatchStart(sessionId, userId);
        return lobbyService.getSnapshot(sessionId, userId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public void deleteSession(
        @PathVariable UUID sessionId,
        @RequestHeader(value = AdminApiKeyGuard.HEADER_NAME, required = false) String adminKey
    ) {
        adminApiKeyGuard.requireAdmin(adminKey);
        sessionAdminService.deleteSession(sessionId);
    }
}
