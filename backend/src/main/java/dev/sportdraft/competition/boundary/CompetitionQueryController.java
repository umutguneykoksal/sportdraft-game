package dev.sportdraft.competition.boundary;

import dev.sportdraft.competition.control.CompetitionQueryService;
import dev.sportdraft.common.realtime.RealtimeConnectionAuthorizer;
import dev.sportdraft.common.web.AuthTokenResolver;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/sessions")
public class CompetitionQueryController {

    private final CompetitionQueryService competitionQueryService;
    private final AuthTokenResolver authTokenResolver;
    private final RealtimeConnectionAuthorizer connectionAuthorizer;

    public CompetitionQueryController(
        CompetitionQueryService competitionQueryService,
        AuthTokenResolver authTokenResolver,
        RealtimeConnectionAuthorizer connectionAuthorizer
    ) {
        this.competitionQueryService = competitionQueryService;
        this.authTokenResolver = authTokenResolver;
        this.connectionAuthorizer = connectionAuthorizer;
    }

    @GetMapping("/{sessionId}/competition")
    public CompetitionResponse getCompetition(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = authTokenResolver.resolveUserId(authorization);
        connectionAuthorizer.assertSessionMembership(sessionId, userId);
        return competitionQueryService.getBySessionId(sessionId, userId);
    }
}
