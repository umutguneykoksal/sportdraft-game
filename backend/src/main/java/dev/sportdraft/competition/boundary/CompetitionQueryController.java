package dev.sportdraft.competition.boundary;

import dev.sportdraft.competition.control.CompetitionQueryService;
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

    public CompetitionQueryController(
        CompetitionQueryService competitionQueryService,
        AuthTokenResolver authTokenResolver
    ) {
        this.competitionQueryService = competitionQueryService;
        this.authTokenResolver = authTokenResolver;
    }

    @GetMapping("/{sessionId}/competition")
    public CompetitionResponse getCompetition(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = authTokenResolver.resolveUserId(authorization);
        return competitionQueryService.getBySessionId(sessionId, userId);
    }
}
