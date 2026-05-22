package com.example.auction.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.auction.bidding.control.AuctionService;
import com.example.auction.bidding.control.CardCatalogService;
import com.example.auction.competition.control.CompetitionMatchService;
import com.example.auction.competition.control.TournamentBracketService;
import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchRepository;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.competition.entity.CompetitionStatus;
import com.example.auction.competition.entity.TournamentPhase;
import com.example.auction.lobby.boundary.SessionSnapshotResponse;
import com.example.auction.lobby.control.LobbyService;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActiveSessionStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TournamentFullBracketIT extends AbstractIntegrationTest {

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
    private TournamentBracketService tournamentBracketService;

    @Test
    void tournamentFullBracket_completesWithChampion() {
        UUID[] users = {
            CompetitionTestFixtures.USER_1,
            CompetitionTestFixtures.USER_2,
            CompetitionTestFixtures.USER_3,
            CompetitionTestFixtures.USER_4
        };

        SessionSnapshotResponse room = lobbyService.createRoom(
            CompetitionTestFixtures.USER_1, "Player-1", GameMode.TOURNAMENT
        );
        UUID sessionId = room.sessionId();
        lobbyService.joinRoom(sessionId, CompetitionTestFixtures.USER_2, "Player-2");
        lobbyService.joinRoom(sessionId, CompetitionTestFixtures.USER_3, "Player-3");
        lobbyService.joinRoom(sessionId, CompetitionTestFixtures.USER_4, "Player-4");

        CompetitionTestFixtures.advanceToAuctioning(lobbyService, sessionStore, sessionId, users);
        assertThat(lobbyService.getSnapshot(sessionId, CompetitionTestFixtures.USER_1).status())
            .isEqualTo(SessionStatus.AUCTIONING);

        CompetitionTestFixtures.completeCompetitionDraft(
            auctionService, cardCatalog, sessionStore, sessionId, users
        );

        await().atMost(10, SECONDS).until(() -> competitionRepository.findBySessionId(sessionId).isPresent());

        CompetitionEntity competition = competitionRepository.findBySessionId(sessionId).orElseThrow();
        UUID competitionId = competition.getId();

        CompetitionMatchEntity semi1 = CompetitionTestFixtures.requireMatch(matchRepository, competitionId, "SEMI_1");
        CompetitionMatchEntity semi2 = CompetitionTestFixtures.requireMatch(matchRepository, competitionId, "SEMI_2");

        CompetitionTestFixtures.watchMatch(competitionMatchService, sessionId, semi1);
        CompetitionTestFixtures.watchMatch(competitionMatchService, sessionId, semi2);

        competition = competitionRepository.findById(competitionId).orElseThrow();
        assertThat(competition.getTournamentPhase()).isEqualTo(TournamentPhase.INTERMISSION);
        assertThat(matchRepository.findByCompetitionIdAndRoundKey(competitionId, "FINAL")).isPresent();

        competition = CompetitionTestFixtures.advanceTournamentIntermission(
            tournamentBracketService, competitionRepository, competition
        );
        assertThat(competition.getTournamentPhase()).isEqualTo(TournamentPhase.FINAL);

        CompetitionMatchEntity finalMatch = CompetitionTestFixtures.requireMatch(matchRepository, competitionId, "FINAL");
        CompetitionTestFixtures.watchMatchWithAllParticipants(
            competitionMatchService,
            sessionId,
            finalMatch,
            users
        );

        competition = competitionRepository.findById(competitionId).orElseThrow();
        assertThat(competition.getStatus()).isEqualTo(CompetitionStatus.COMPLETED);
        assertThat(competition.getTournamentPhase()).isEqualTo(TournamentPhase.REPLAYS);
        assertThat(competition.getChampionUserId()).isNotNull();
    }
}
