package dev.sportdraft.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sportdraft.competition.control.CompetitionMatchService;
import dev.sportdraft.competition.control.TournamentBracketService;
import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchRepository;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.competition.entity.TournamentPhase;
import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import dev.sportdraft.simulation.control.DraftCompletedEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class TournamentNegativeIT extends AbstractIntegrationTest {

    @Autowired
    private TournamentBracketService tournamentBracketService;

    @Autowired
    private CompetitionMatchService competitionMatchService;

    @Autowired
    private CompetitionRepository competitionRepository;

    @Autowired
    private CompetitionMatchRepository matchRepository;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Test
    void openMatch_rejectsDuringIntermission() {
        UUID sessionId = UUID.randomUUID();
        seedSession(sessionId);
        CompetitionEntity competition = tournamentBracketService.initializeTournament(
            CompetitionTestFixtures.tournamentEvent(sessionId)
        );

        CompetitionMatchEntity semi1 = CompetitionTestFixtures.requireMatch(matchRepository, competition.getId(), "SEMI_1");
        CompetitionMatchEntity semi2 = CompetitionTestFixtures.requireMatch(matchRepository, competition.getId(), "SEMI_2");

        CompetitionTestFixtures.watchMatch(competitionMatchService, sessionId, semi1);
        CompetitionTestFixtures.watchMatch(competitionMatchService, sessionId, semi2);

        CompetitionEntity updated = competitionRepository.findById(competition.getId()).orElseThrow();
        assertThat(updated.getTournamentPhase()).isEqualTo(TournamentPhase.INTERMISSION);

        assertThatThrownBy(() -> competitionMatchService.openMatch(
            sessionId, semi1.getId(), CompetitionTestFixtures.USER_1, false
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("Semi-final results — final starts shortly");
            });
    }

    @Test
    void openMatch_rejectsOutsiderFromSemi() {
        UUID sessionId = UUID.randomUUID();
        seedSession(sessionId);
        CompetitionEntity competition = tournamentBracketService.initializeTournament(
            CompetitionTestFixtures.tournamentEvent(sessionId)
        );
        CompetitionMatchEntity semi1 = CompetitionTestFixtures.requireMatch(matchRepository, competition.getId(), "SEMI_1");

        assertThatThrownBy(() -> competitionMatchService.openMatch(
            sessionId, semi1.getId(), CompetitionTestFixtures.USER_3, false
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(rse.getReason()).isEqualTo("You can only watch your own semi-final");
            });
    }

    private void seedSession(UUID sessionId) {
        ActiveSession session = CompetitionTestFixtures.activeSession(sessionId, GameMode.TOURNAMENT);
        sessionStore.save(session);
    }
}
