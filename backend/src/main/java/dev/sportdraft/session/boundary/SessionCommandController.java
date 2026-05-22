package dev.sportdraft.session.boundary;

import dev.sportdraft.common.web.AuthTokenResolver;
import dev.sportdraft.lobby.boundary.SessionSnapshotResponse;
import dev.sportdraft.session.control.SessionOrchestrator;
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

    private final SessionOrchestrator sessionOrchestrator;
    private final AuthTokenResolver authTokenResolver;

    public SessionCommandController(SessionOrchestrator sessionOrchestrator, AuthTokenResolver authTokenResolver) {
        this.sessionOrchestrator = sessionOrchestrator;
        this.authTokenResolver = authTokenResolver;
    }

    @DeleteMapping("/sessions/{sessionId}/players/me")
    public SessionSnapshotResponse leaveLobby(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        return sessionOrchestrator.leaveLobby(sessionId, authTokenResolver.resolveUserId(authorization));
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionSnapshotResponse getSnapshot(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        return sessionOrchestrator.getSnapshot(sessionId, authTokenResolver.resolveUserId(authorization));
    }

    @PostMapping("/sessions/{sessionId}/formation")
    public SessionSnapshotResponse selectFormation(
        @PathVariable UUID sessionId,
        @Valid @RequestBody SelectFormationRequest request,
        @RequestHeader("Authorization") String authorization
    ) {
        return sessionOrchestrator.selectFormation(
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
        return sessionOrchestrator.confirmFormationReady(sessionId, authTokenResolver.resolveUserId(authorization));
    }

    @PostMapping("/sessions/{sessionId}/confirm-match-start")
    public SessionSnapshotResponse confirmMatchStart(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        return sessionOrchestrator.confirmMatchStart(sessionId, authTokenResolver.resolveUserId(authorization));
    }
}
