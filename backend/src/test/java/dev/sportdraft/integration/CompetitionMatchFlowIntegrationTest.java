package dev.sportdraft.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sportdraft.competition.boundary.CompetitionMatchOpenResponse;
import dev.sportdraft.competition.boundary.CompetitionResponse;
import dev.sportdraft.competition.control.CompetitionMatchService;
import dev.sportdraft.competition.control.CompetitionQueryService;
import dev.sportdraft.competition.control.LeagueFixtureService;
import dev.sportdraft.competition.control.TournamentBracketService;
import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchRepository;
import dev.sportdraft.competition.entity.CompetitionMatchStatus;
import dev.sportdraft.competition.entity.CompetitionMatchViewRepository;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.competition.entity.LeaguePhase;
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

class CompetitionMatchFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TournamentBracketService tournamentBracketService;

    @Autowired
    private LeagueFixtureService leagueFixtureService;

    @Autowired
    private CompetitionMatchService competitionMatchService;

    @Autowired
    private CompetitionQueryService competitionQueryService;

    @Autowired
    private CompetitionRepository competitionRepository;

    @Autowired
    private CompetitionMatchRepository matchRepository;

    @Autowired
    private CompetitionMatchViewRepository viewRepository;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Test
    void tournamentSemiParticipantCanOpenAndAckWatch() {
        UUID sessionId = UUID.randomUUID();
        seedSession(sessionId, GameMode.TOURNAMENT);
        DraftCompletedEvent event = CompetitionTestFixtures.tournamentEvent(sessionId);
        CompetitionEntity competition = tournamentBracketService.initializeTournament(event);

        CompetitionMatchEntity semi1 = matchRepository
            .findByCompetitionIdAndRoundKey(competition.getId(), "SEMI_1")
            .orElseThrow();

        CompetitionMatchOpenResponse open = competitionMatchService.openMatch(
            sessionId, semi1.getId(), CompetitionTestFixtures.USER_1, false
        );
        assertThat(open.matchId()).isEqualTo(semi1.getId());
        assertThat(open.timeline()).isNotEmpty();
        assertThat(open.homeScore()).isNull();
        assertThat(open.awayScore()).isNull();

        CompetitionMatchEntity completed = matchRepository.findById(semi1.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(CompetitionMatchStatus.COMPLETED);

        competitionMatchService.acknowledgeWatch(sessionId, semi1.getId(), CompetitionTestFixtures.USER_1);
        competitionMatchService.acknowledgeWatch(sessionId, semi1.getId(), CompetitionTestFixtures.USER_2);

        assertThat(viewRepository.existsByMatchIdAndUserId(semi1.getId(), CompetitionTestFixtures.USER_1)).isTrue();
        assertThat(viewRepository.existsByMatchIdAndUserId(semi1.getId(), CompetitionTestFixtures.USER_2)).isTrue();
        assertThat(viewRepository.countByMatchId(semi1.getId())).isEqualTo(2);
    }

    @Test
    void tournamentBothSemisWatchedStartsIntermission() {
        UUID sessionId = UUID.randomUUID();
        seedSession(sessionId, GameMode.TOURNAMENT);
        CompetitionEntity competition = tournamentBracketService.initializeTournament(
            CompetitionTestFixtures.tournamentEvent(sessionId)
        );

        CompetitionMatchEntity semi1 = CompetitionTestFixtures.requireMatch(matchRepository, competition.getId(), "SEMI_1");
        CompetitionMatchEntity semi2 = CompetitionTestFixtures.requireMatch(matchRepository, competition.getId(), "SEMI_2");

        CompetitionTestFixtures.watchMatch(competitionMatchService, sessionId, semi1);
        CompetitionTestFixtures.watchMatch(competitionMatchService, sessionId, semi2);

        CompetitionEntity updated = competitionRepository.findById(competition.getId()).orElseThrow();
        assertThat(updated.getTournamentPhase()).isEqualTo(TournamentPhase.INTERMISSION);
        assertThat(updated.getIntermissionEndsAt()).isNotNull();
        assertThat(matchRepository.findByCompetitionIdAndRoundKey(competition.getId(), "FINAL")).isPresent();
    }

    @Test
    void leagueMatchdayParticipantCanOpenAndAckWatch() {
        UUID sessionId = UUID.randomUUID();
        seedSession(sessionId, GameMode.LEAGUE);
        CompetitionEntity competition = leagueFixtureService.initializeLeague(
            CompetitionTestFixtures.leagueEvent(sessionId)
        );

        CompetitionMatchEntity md1Match = matchRepository
            .findByCompetitionIdOrderBySortOrderAsc(competition.getId())
            .stream()
            .filter(m -> "MD1_A".equals(m.getRoundKey()))
            .findFirst()
            .orElseThrow();

        competitionMatchService.openMatch(sessionId, md1Match.getId(), CompetitionTestFixtures.USER_1, false);
        competitionMatchService.acknowledgeWatch(sessionId, md1Match.getId(), CompetitionTestFixtures.USER_1);
        competitionMatchService.acknowledgeWatch(sessionId, md1Match.getId(), CompetitionTestFixtures.USER_2);

        assertThat(viewRepository.countByMatchId(md1Match.getId())).isEqualTo(2);

        CompetitionResponse query = competitionQueryService.getBySessionId(sessionId, CompetitionTestFixtures.USER_1);
        assertThat(query.leaguePhase()).isEqualTo("MATCHDAY_1");
        assertThat(query.matches()).isNotEmpty();
    }

    @Test
    void replayBeforeReplaysPhaseIsRejected() {
        UUID sessionId = UUID.randomUUID();
        seedSession(sessionId, GameMode.TOURNAMENT);
        CompetitionEntity competition = tournamentBracketService.initializeTournament(
            CompetitionTestFixtures.tournamentEvent(sessionId)
        );
        CompetitionMatchEntity semi1 = CompetitionTestFixtures.requireMatch(matchRepository, competition.getId(), "SEMI_1");

        assertThatThrownBy(() -> competitionMatchService.openMatch(
            sessionId, semi1.getId(), CompetitionTestFixtures.USER_1, true
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void missingCompetitionReturnsNotFound() {
        UUID sessionId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();

        assertThatThrownBy(() -> competitionMatchService.openMatch(
            sessionId, matchId, CompetitionTestFixtures.USER_1, false
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void leagueMatchdayBreak_startsAfterBothMd1MatchesWatched() {
        UUID sessionId = UUID.randomUUID();
        seedSession(sessionId, GameMode.LEAGUE);
        CompetitionEntity competition = leagueFixtureService.initializeLeague(
            CompetitionTestFixtures.leagueEvent(sessionId)
        );

        CompetitionMatchEntity md1A = CompetitionTestFixtures.requireMatch(matchRepository, competition.getId(), "MD1_A");
        CompetitionMatchEntity md1B = CompetitionTestFixtures.requireMatch(matchRepository, competition.getId(), "MD1_B");

        CompetitionTestFixtures.watchMatch(competitionMatchService, sessionId, md1A);
        CompetitionTestFixtures.watchMatch(competitionMatchService, sessionId, md1B);

        CompetitionEntity updated = competitionRepository.findById(competition.getId()).orElseThrow();
        assertThat(updated.getLeaguePhase()).isEqualTo(LeaguePhase.BREAK_AFTER_MD1);
        assertThat(updated.getIntermissionEndsAt()).isNotNull();
    }

    private void seedSession(UUID sessionId, GameMode gameMode) {
        ActiveSession session = CompetitionTestFixtures.activeSession(sessionId, gameMode);
        sessionStore.save(session);
    }

}
