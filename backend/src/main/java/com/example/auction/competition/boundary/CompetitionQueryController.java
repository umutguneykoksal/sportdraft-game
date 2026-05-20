package com.example.auction.competition.boundary;

import com.example.auction.competition.control.CompetitionQueryService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/sessions")
public class CompetitionQueryController {

    private final CompetitionQueryService competitionQueryService;

    public CompetitionQueryController(CompetitionQueryService competitionQueryService) {
        this.competitionQueryService = competitionQueryService;
    }

    @GetMapping("/{sessionId}/competition")
    public CompetitionResponse getCompetition(@PathVariable UUID sessionId) {
        return competitionQueryService.getBySessionId(sessionId);
    }
}
