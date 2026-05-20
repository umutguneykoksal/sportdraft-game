package com.example.auction.competition.boundary;

import com.example.auction.competition.control.TournamentMatchService;
import com.example.auction.common.web.AuthTokenResolver;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/sessions")
public class CompetitionCommandController {

    private final TournamentMatchService tournamentMatchService;
    private final AuthTokenResolver authTokenResolver;

    public CompetitionCommandController(
        TournamentMatchService tournamentMatchService,
        AuthTokenResolver authTokenResolver
    ) {
        this.tournamentMatchService = tournamentMatchService;
        this.authTokenResolver = authTokenResolver;
    }

    @PostMapping("/{sessionId}/competition/matches/{matchId}/open")
    public CompetitionMatchOpenResponse openMatch(
        @PathVariable UUID sessionId,
        @PathVariable UUID matchId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = authTokenResolver.resolveUserId(authorization);
        return tournamentMatchService.openMatch(sessionId, matchId, userId);
    }

    @PostMapping("/{sessionId}/competition/matches/{matchId}/ack-watch")
    public CompetitionResponse acknowledgeWatch(
        @PathVariable UUID sessionId,
        @PathVariable UUID matchId,
        @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = authTokenResolver.resolveUserId(authorization);
        return tournamentMatchService.acknowledgeWatch(sessionId, matchId, userId);
    }
}
