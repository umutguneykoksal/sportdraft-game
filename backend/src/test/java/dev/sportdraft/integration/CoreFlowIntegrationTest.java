package dev.sportdraft.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sportdraft.bidding.boundary.AuctionRoundResponse;
import dev.sportdraft.bidding.boundary.PlaceBidRequest;
import dev.sportdraft.bidding.control.AuctionService;
import dev.sportdraft.lobby.boundary.SessionSnapshotResponse;
import dev.sportdraft.lobby.control.LobbyService;
import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CoreFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Test
    void lobbyAuctionCoreFlowWorks() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        SessionSnapshotResponse firstJoin = lobbyService.createRoom(user1, "TestPlayer1");
        SessionSnapshotResponse secondJoin = lobbyService.joinRoom(firstJoin.sessionId(), user2, "TestPlayer2");
        UUID sessionId = secondJoin.sessionId();
        assertThat(firstJoin.sessionId()).isEqualTo(sessionId);

        ActiveSession session = sessionStore.getById(sessionId);
        long v = session.getVersion();
        session.moveToCountdown(Instant.now().minusSeconds(1));
        sessionStore.saveIfVersionMatch(session, v);
        lobbyService.advanceTimedPhases();

        SessionSnapshotResponse afterFormationStart = lobbyService.getSnapshot(sessionId, user1);
        assertThat(afterFormationStart.status()).isEqualTo(SessionStatus.FORMATION_SELECT);

        lobbyService.selectFormation(sessionId, user1, Formation.FOUR_FOUR_TWO);
        lobbyService.selectFormation(sessionId, user2, Formation.FOUR_THREE_THREE);
        SessionSnapshotResponse afterFirstReady = lobbyService.confirmFormationReady(sessionId, user1);
        assertThat(afterFirstReady.status()).isEqualTo(SessionStatus.FORMATION_SELECT);
        assertThat(afterFirstReady.matchStartReadyCount()).isEqualTo(1);

        SessionSnapshotResponse afterSecondReady = lobbyService.confirmFormationReady(sessionId, user2);
        assertThat(afterSecondReady.status()).isEqualTo(SessionStatus.AUCTIONING);

        SessionSnapshotResponse auctioning = lobbyService.getSnapshot(sessionId, user1);
        assertThat(auctioning.status()).isEqualTo(SessionStatus.AUCTIONING);

        AuctionRoundResponse round = auctionService.getAuctionState(sessionId, user1).currentRound();
        assertThat(round).isNotNull();
        IntegrationAuctionTestSupport.openBiddingOnCurrentRound(sessionStore, sessionId);
        AuctionRoundResponse afterBid = auctionService.placeBid(
            sessionId, user1, UUID.randomUUID().toString(),
            new PlaceBidRequest(round.roundId(), 2)
        );
        assertThat(afterBid.roundId()).isEqualTo(round.roundId());
    }
}
