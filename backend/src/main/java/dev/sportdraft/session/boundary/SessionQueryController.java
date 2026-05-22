package dev.sportdraft.session.boundary;

import dev.sportdraft.common.web.AuthTokenResolver;
import dev.sportdraft.session.control.SessionReadService;
import dev.sportdraft.simulation.boundary.MatchResultResponse;
import dev.sportdraft.simulation.entity.MatchEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/sessions")
public class SessionQueryController {

    private final SessionReadService sessionReadService;
    private final AuthTokenResolver authTokenResolver;

    public SessionQueryController(SessionReadService sessionReadService, AuthTokenResolver authTokenResolver) {
        this.sessionReadService = sessionReadService;
        this.authTokenResolver = authTokenResolver;
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
}
