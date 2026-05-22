package dev.sportdraft.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.sportdraft.bidding.control.AuctionService;
import dev.sportdraft.bidding.control.CardCatalogService;
import dev.sportdraft.lobby.boundary.SessionSnapshotResponse;
import dev.sportdraft.lobby.control.LobbyService;
import dev.sportdraft.session.control.SessionReadService;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class DuelMatchFlowIT extends AbstractIntegrationTest {

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private CardCatalogService cardCatalog;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Autowired
    private SessionReadService sessionReadService;

    @Test
    void duelDraftToMatchResult_finishesWithScores() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        SessionSnapshotResponse firstJoin = lobbyService.joinLobby(user1, "DuelPlayer1");
        SessionSnapshotResponse secondJoin = lobbyService.joinLobby(user2, "DuelPlayer2");
        UUID sessionId = secondJoin.sessionId();
        assertThat(firstJoin.sessionId()).isEqualTo(sessionId);

        CompetitionTestFixtures.advanceToAuctioning(lobbyService, sessionStore, sessionId, user1, user2);

        assertThat(lobbyService.getSnapshot(sessionId, user1).status()).isEqualTo(SessionStatus.AUCTIONING);

        CompetitionTestFixtures.completeDuelDraft(auctionService, cardCatalog, sessionStore, sessionId, user1, user2);

        await().atMost(10, SECONDS).until(() ->
            sessionStore.getById(sessionId).getStatus() == SessionStatus.FINISHED
        );

        ActiveSession finished = sessionStore.getById(sessionId);
        assertThat(finished.getHomeScore()).isNotNull();
        assertThat(finished.getAwayScore()).isNotNull();
        assertThat(finished.getMatchWinnerUserId()).isNotNull();
        assertThat(finished.getMatchTimeline()).isNotEmpty();

        ResponseEntity<?> result = sessionReadService.getMatchResult(sessionId, user1);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
