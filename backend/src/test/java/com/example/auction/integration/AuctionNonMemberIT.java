package com.example.auction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auction.bidding.boundary.AuctionRoundResponse;
import com.example.auction.bidding.boundary.PlaceBidRequest;
import com.example.auction.bidding.control.AuctionService;
import com.example.auction.lobby.control.LobbyService;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AuctionNonMemberIT extends AbstractIntegrationTest {

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Test
    void placeBid_rejectsNonMember() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();

        UUID sessionId = lobbyService.joinLobby(user1, "Member1").sessionId();
        lobbyService.joinLobby(user2, "Member2");

        ActiveSession session = sessionStore.getById(sessionId);
        long version = session.getVersion();
        session.moveToCountdown(Instant.now().minusSeconds(1));
        sessionStore.saveIfVersionMatch(session, version);
        lobbyService.advanceTimedPhases();

        lobbyService.selectFormation(sessionId, user1, Formation.FOUR_FOUR_TWO);
        lobbyService.selectFormation(sessionId, user2, Formation.FOUR_THREE_THREE);
        lobbyService.confirmFormationReady(sessionId, user1);
        lobbyService.confirmFormationReady(sessionId, user2);

        session = sessionStore.getById(sessionId);
        version = session.getVersion();
        session.setCountdownEndsAt(Instant.now().minusSeconds(1));
        sessionStore.saveIfVersionMatch(session, version);
        lobbyService.advanceTimedPhases();

        assertThatThrownBy(() -> auctionService.getAuctionState(sessionId, outsider))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(rse.getReason()).isEqualTo("Player not found in session");
            });

        AuctionRoundResponse round = auctionService.getAuctionState(sessionId, user1).currentRound();
        assertThatThrownBy(() -> auctionService.placeBid(
            sessionId,
            outsider,
            UUID.randomUUID().toString(),
            new PlaceBidRequest(round.roundId(), 1)
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(rse.getReason()).isEqualTo("Player not found in session");
            });
    }
}
