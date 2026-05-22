package dev.sportdraft.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sportdraft.bidding.boundary.AuctionRoundResponse;
import dev.sportdraft.bidding.boundary.PlaceBidRequest;
import dev.sportdraft.bidding.control.AuctionService;
import dev.sportdraft.lobby.control.LobbyService;
import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuctionIdempotencyIT extends AbstractIntegrationTest {

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Test
    void placeBid_withSameIdempotencyKey_isIdempotent() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        UUID sessionId = lobbyService.joinLobby(user1, "Idem1").sessionId();
        lobbyService.joinLobby(user2, "Idem2");

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

        assertThat(lobbyService.getSnapshot(sessionId, user1).status()).isEqualTo(SessionStatus.AUCTIONING);

        AuctionRoundResponse round = auctionService.getAuctionState(sessionId, user1).currentRound();
        assertThat(round).isNotNull();

        String idempotencyKey = UUID.randomUUID().toString();
        PlaceBidRequest request = new PlaceBidRequest(round.roundId(), 2);

        AuctionRoundResponse first = auctionService.placeBid(sessionId, user1, idempotencyKey, request);
        AuctionRoundResponse second = auctionService.placeBid(sessionId, user1, idempotencyKey, request);

        assertThat(second.roundId()).isEqualTo(first.roundId());
        assertThat(second.highestBidCents()).isEqualTo(first.highestBidCents());

        ActiveSession after = sessionStore.getById(sessionId);
        long bidCount = after.getRounds().stream()
            .filter(r -> r.getId().equals(round.roundId()))
            .findFirst()
            .orElseThrow()
            .getBids()
            .size();
        assertThat(bidCount).isEqualTo(1);
    }
}
