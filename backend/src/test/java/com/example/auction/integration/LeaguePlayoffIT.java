package com.example.auction.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.auction.bidding.control.AuctionService;
import com.example.auction.bidding.control.CardCatalogService;
import com.example.auction.competition.control.CompetitionMatchService;
import com.example.auction.competition.control.LeagueFixtureService;
import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchRepository;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.competition.entity.CompetitionStatus;
import com.example.auction.competition.entity.LeaguePhase;
import com.example.auction.lobby.boundary.SessionSnapshotResponse;
import com.example.auction.lobby.control.LobbyService;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.redis.ActiveSessionStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LeaguePlayoffIT extends AbstractIntegrationTest {

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private CardCatalogService cardCatalog;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Autowired
    private CompetitionRepository competitionRepository;

    @Autowired
    private CompetitionMatchRepository matchRepository;

    @Autowired
    private CompetitionMatchService competitionMatchService;

    @Autowired
    private LeagueFixtureService leagueFixtureService;

    @Test
    void leagueTwoWayTie_completesViaPlayoffFinal() {
        UUID sessionId = setupLeagueCompetition();
        CompetitionEntity competition = competitionRepository.findBySessionId(sessionId).orElseThrow();
        UUID competitionId = competition.getId();

        // USER_1 and USER_2 finish tied at 5 points; USER_3=4, USER_4=1
        CompetitionTestFixtures.watchMatchdayWithResults(
            competitionMatchService, matchRepository, sessionId, competitionId, 1,
            new int[][] {{1, 1}, {2, 0}}
        );
        competition = CompetitionTestFixtures.advanceLeagueBreak(
            leagueFixtureService, competitionRepository, competition
        );

        CompetitionTestFixtures.watchMatchdayWithResults(
            competitionMatchService, matchRepository, sessionId, competitionId, 2,
            new int[][] {{2, 0}, {2, 0}}
        );
        competition = CompetitionTestFixtures.advanceLeagueBreak(
            leagueFixtureService, competitionRepository, competition
        );

        CompetitionTestFixtures.watchMatchdayWithResults(
            competitionMatchService, matchRepository, sessionId, competitionId, 3,
            new int[][] {{1, 1}, {1, 1}}
        );

        competition = competitionRepository.findById(competitionId).orElseThrow();
        assertThat(competition.getLeaguePhase()).isEqualTo(LeaguePhase.CHAMPIONSHIP_PLAYOFF);

        CompetitionMatchEntity playoff = CompetitionTestFixtures.requireMatch(
            matchRepository, competitionId, "PLAYOFF_FINAL"
        );
        CompetitionTestFixtures.watchMatch(competitionMatchService, sessionId, playoff);

        competition = competitionRepository.findById(competitionId).orElseThrow();
        assertThat(competition.getStatus()).isEqualTo(CompetitionStatus.COMPLETED);
        assertThat(competition.getLeaguePhase()).isEqualTo(LeaguePhase.REPLAYS);
        assertThat(competition.getChampionUserId()).isNotNull();
    }

    private UUID setupLeagueCompetition() {
        UUID[] users = {
            CompetitionTestFixtures.USER_1,
            CompetitionTestFixtures.USER_2,
            CompetitionTestFixtures.USER_3,
            CompetitionTestFixtures.USER_4
        };

        SessionSnapshotResponse room = lobbyService.createRoom(
            CompetitionTestFixtures.USER_1, "Player-1", GameMode.LEAGUE
        );
        UUID sessionId = room.sessionId();
        lobbyService.joinRoom(sessionId, CompetitionTestFixtures.USER_2, "Player-2");
        lobbyService.joinRoom(sessionId, CompetitionTestFixtures.USER_3, "Player-3");
        lobbyService.joinRoom(sessionId, CompetitionTestFixtures.USER_4, "Player-4");

        CompetitionTestFixtures.advanceToAuctioning(lobbyService, sessionStore, sessionId, users);
        CompetitionTestFixtures.completeCompetitionDraft(
            auctionService, cardCatalog, sessionStore, sessionId, users
        );

        await().atMost(10, SECONDS).until(() -> competitionRepository.findBySessionId(sessionId).isPresent());
        return sessionId;
    }
}
