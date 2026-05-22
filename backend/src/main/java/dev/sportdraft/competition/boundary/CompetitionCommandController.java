package dev.sportdraft.competition.boundary;

import dev.sportdraft.competition.control.CompetitionMatchService;
import dev.sportdraft.common.web.AuthTokenResolver;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/sessions")
public class CompetitionCommandController {

    private final CompetitionMatchService competitionMatchService;
    private final AuthTokenResolver authTokenResolver;

    public CompetitionCommandController(
        CompetitionMatchService competitionMatchService,
        AuthTokenResolver authTokenResolver
    ) {
        this.competitionMatchService = competitionMatchService;
        this.authTokenResolver = authTokenResolver;
    }

    @PostMapping("/{sessionId}/competition/matches/{matchId}/open")
    public CompetitionMatchOpenResponse openMatch(
        @PathVariable UUID sessionId,
        @PathVariable UUID matchId,
        @RequestHeader("Authorization") String authorization,
        @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean replay
    ) {
        UUID userId = authTokenResolver.resolveUserId(authorization);
        return competitionMatchService.openMatch(sessionId, matchId, userId, replay);
    }

    @PostMapping("/{sessionId}/competition/matches/{matchId}/ack-watch")
    public CompetitionResponse acknowledgeWatch(
        @PathVariable UUID sessionId,
        @PathVariable UUID matchId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = authTokenResolver.resolveUserId(authorization);
        return competitionMatchService.acknowledgeWatch(sessionId, matchId, userId);
    }
}
