package com.example.auction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auction.bidding.boundary.AuctionRoundResponse;
import com.example.auction.bidding.boundary.PlaceBidRequest;
import com.example.auction.bidding.control.AuctionService;
import com.example.auction.bidding.control.CardCatalogService;
import com.example.auction.lobby.control.LobbyService;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AuctionMatchStartPendingIT extends AbstractIntegrationTest {

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private CardCatalogService cardCatalog;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Test
    void fullRostersBid_triggersMatchStartPending() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        UUID sessionId = lobbyService.joinLobby(user1, "Pending1").sessionId();
        lobbyService.joinLobby(user2, "Pending2");
        CompetitionTestFixtures.advanceToAuctioning(lobbyService, sessionStore, sessionId, user1, user2);

        auctionService.ensureOpenRound(sessionId);

        ActiveSession session = sessionStore.getById(sessionId);
        CompetitionTestFixtures.fillFullRosters(session, cardCatalog, sessionStore);

        AuctionRoundResponse round = auctionService.getAuctionState(sessionId, user1).currentRound();
        assertThat(round).isNotNull();

        assertThatThrownBy(() -> auctionService.placeBid(
            sessionId,
            user1,
            UUID.randomUUID().toString(),
            new PlaceBidRequest(round.roundId(), 1)
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("Rosters complete; confirm match start");
            });

        assertThat(sessionStore.getById(sessionId).getStatus()).isEqualTo(SessionStatus.MATCH_START_PENDING);
    }
}
