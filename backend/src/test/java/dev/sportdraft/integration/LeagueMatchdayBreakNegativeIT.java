package dev.sportdraft.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sportdraft.competition.control.CompetitionMatchService;
import dev.sportdraft.competition.control.LeagueFixtureService;
import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchRepository;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.competition.entity.LeaguePhase;
import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class LeagueMatchdayBreakNegativeIT extends AbstractIntegrationTest {

    @Autowired
    private LeagueFixtureService leagueFixtureService;

    @Autowired
    private CompetitionMatchService competitionMatchService;

    @Autowired
    private CompetitionRepository competitionRepository;

    @Autowired
    private CompetitionMatchRepository matchRepository;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Test
    void openMatch_rejectsDuringMatchdayBreak() {
        UUID sessionId = UUID.randomUUID();
        seedSession(sessionId);
        CompetitionEntity competition = leagueFixtureService.initializeLeague(
            CompetitionTestFixtures.leagueEvent(sessionId)
        );

        CompetitionMatchEntity md1A = CompetitionTestFixtures.requireMatch(matchRepository, competition.getId(), "MD1_A");
        CompetitionMatchEntity md1B = CompetitionTestFixtures.requireMatch(matchRepository, competition.getId(), "MD1_B");

        CompetitionTestFixtures.watchMatch(competitionMatchService, sessionId, md1A);
        CompetitionTestFixtures.watchMatch(competitionMatchService, sessionId, md1B);

        CompetitionEntity updated = competitionRepository.findById(competition.getId()).orElseThrow();
        assertThat(updated.getLeaguePhase()).isEqualTo(LeaguePhase.BREAK_AFTER_MD1);

        assertThatThrownBy(() -> competitionMatchService.openMatch(
            sessionId, md1A.getId(), CompetitionTestFixtures.USER_1, false
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("Matchday break — next round starts shortly");
            });
    }

    private void seedSession(UUID sessionId) {
        ActiveSession session = CompetitionTestFixtures.activeSession(sessionId, GameMode.LEAGUE);
        sessionStore.save(session);
    }
}
