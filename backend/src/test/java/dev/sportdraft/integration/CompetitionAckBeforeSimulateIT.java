package dev.sportdraft.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sportdraft.competition.control.CompetitionMatchService;
import dev.sportdraft.competition.control.TournamentBracketService;
import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchRepository;
import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import dev.sportdraft.simulation.control.DraftCompletedEvent;
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
