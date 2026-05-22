package com.example.auction.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.auction.bidding.control.AuctionService;
import com.example.auction.bidding.control.CardCatalogService;
import com.example.auction.competition.boundary.CompetitionMatchOpenResponse;
import com.example.auction.competition.control.CompetitionMatchService;
import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchRepository;
import com.example.auction.competition.entity.CompetitionMatchStatus;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.lobby.boundary.SessionSnapshotResponse;
import com.example.auction.lobby.control.LobbyService;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TournamentDraftToCompetitionIT extends AbstractIntegrationTest {

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

    @Test
    void tournamentLobbyToSemiOpen_createsCompetitionViaDraftCompleted() {
        SessionSnapshotResponse room = lobbyService.createRoom(
            CompetitionTestFixtures.USER_1, "Player-1", GameMode.TOURNAMENT
        );
        UUID sessionId = room.sessionId();
        lobbyService.joinRoom(sessionId, CompetitionTestFixtures.USER_2, "Player-2");
        lobbyService.joinRoom(sessionId, CompetitionTestFixtures.USER_3, "Player-3");
        lobbyService.joinRoom(sessionId, CompetitionTestFixtures.USER_4, "Player-4");

        UUID[] users = {
            CompetitionTestFixtures.USER_1,
            CompetitionTestFixtures.USER_2,
            CompetitionTestFixtures.USER_3,
            CompetitionTestFixtures.USER_4
        };
        CompetitionTestFixtures.advanceToAuctioning(lobbyService, sessionStore, sessionId, users);

        assertThat(lobbyService.getSnapshot(sessionId, CompetitionTestFixtures.USER_1).status())
            .isEqualTo(SessionStatus.AUCTIONING);

        ActiveSession session = sessionStore.getById(sessionId);
        CompetitionTestFixtures.fillFullRosters(session, cardCatalog, sessionStore);
        auctionService.ensureOpenRound(sessionId);

        for (UUID userId : users) {
            auctionService.confirmMatchStart(sessionId, userId);
        }

        await().atMost(10, SECONDS).until(() -> competitionRepository.findBySessionId(sessionId).isPresent());

        CompetitionEntity competition = competitionRepository.findBySessionId(sessionId).orElseThrow();
        CompetitionMatchEntity semi1 = matchRepository
            .findByCompetitionIdAndRoundKey(competition.getId(), "SEMI_1")
            .orElseThrow();

        CompetitionMatchOpenResponse open = competitionMatchService.openMatch(
            sessionId, semi1.getId(), CompetitionTestFixtures.USER_1, false
        );
        assertThat(open.matchId()).isEqualTo(semi1.getId());
        assertThat(open.timeline()).isNotEmpty();

        CompetitionMatchEntity completed = matchRepository.findById(semi1.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(CompetitionMatchStatus.COMPLETED);
    }

    @Test
    void leagueLobbyToCompetition_createsSixFixtures() {
        SessionSnapshotResponse room = lobbyService.createRoom(
            CompetitionTestFixtures.USER_1, "Player-1", GameMode.LEAGUE
        );
        UUID sessionId = room.sessionId();
        lobbyService.joinRoom(sessionId, CompetitionTestFixtures.USER_2, "Player-2");
        lobbyService.joinRoom(sessionId, CompetitionTestFixtures.USER_3, "Player-3");
        lobbyService.joinRoom(sessionId, CompetitionTestFixtures.USER_4, "Player-4");

        UUID[] users = {
            CompetitionTestFixtures.USER_1,
            CompetitionTestFixtures.USER_2,
            CompetitionTestFixtures.USER_3,
            CompetitionTestFixtures.USER_4
        };
        CompetitionTestFixtures.advanceToAuctioning(lobbyService, sessionStore, sessionId, users);

        ActiveSession session = sessionStore.getById(sessionId);
        CompetitionTestFixtures.fillFullRosters(session, cardCatalog, sessionStore);
        auctionService.ensureOpenRound(sessionId);

        for (UUID userId : users) {
            auctionService.confirmMatchStart(sessionId, userId);
        }

        await().atMost(10, SECONDS).until(() -> competitionRepository.findBySessionId(sessionId).isPresent());

        CompetitionEntity competition = competitionRepository.findBySessionId(sessionId).orElseThrow();
        assertThat(matchRepository.findByCompetitionIdOrderBySortOrderAsc(competition.getId())).hasSize(6);
    }
}
