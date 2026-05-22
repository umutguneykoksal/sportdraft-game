package com.example.auction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auction.competition.control.CompetitionMatchService;
import com.example.auction.competition.control.TournamentBracketService;
import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchRepository;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.simulation.control.DraftCompletedEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CompetitionAckBeforeSimulateIT extends AbstractIntegrationTest {

    @Autowired
    private TournamentBracketService tournamentBracketService;

    @Autowired
    private CompetitionMatchService competitionMatchService;

    @Autowired
    private CompetitionMatchRepository matchRepository;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Test
    void acknowledgeWatch_rejectsWhenMatchNotSimulated() {
        UUID sessionId = UUID.randomUUID();
        seedSession(sessionId);
        CompetitionEntity competition = tournamentBracketService.initializeTournament(
            CompetitionTestFixtures.tournamentEvent(sessionId)
        );
        CompetitionMatchEntity semi1 = CompetitionTestFixtures.requireMatch(
            matchRepository, competition.getId(), "SEMI_1"
        );

        assertThatThrownBy(() -> competitionMatchService.acknowledgeWatch(
            sessionId, semi1.getId(), CompetitionTestFixtures.USER_1
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("Match has not been simulated yet");
            });
    }

    private void seedSession(UUID sessionId) {
        ActiveSession session = CompetitionTestFixtures.activeSession(sessionId, GameMode.TOURNAMENT);
        sessionStore.save(session);
    }
}
