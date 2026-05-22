package com.example.auction.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auction.bidding.boundary.AuctionRoundResponse;
import com.example.auction.bidding.boundary.PlaceBidRequest;
import com.example.auction.bidding.control.AuctionService;
import com.example.auction.common.realtime.SessionEventLog;
import com.example.auction.lobby.boundary.SessionSnapshotResponse;
import com.example.auction.lobby.control.LobbyService;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CommandToRealtimeFlowIT extends AbstractIntegrationTest {

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Autowired
    private SessionEventLog sessionEventLog;

    @Test
    void placeBid_appendsToSessionEventLog() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        SessionSnapshotResponse joined = lobbyService.joinLobby(user1, "BidFlow1");
        lobbyService.joinLobby(user2, "BidFlow2");
        UUID sessionId = joined.sessionId();

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
        auctionService.placeBid(
            sessionId,
            user1,
            UUID.randomUUID().toString(),
            new PlaceBidRequest(round.roundId(), 2)
        );

        List<Map<String, Object>> events = sessionEventLog.since(sessionId, 0);
        assertThat(events).isNotEmpty();

        List<Long> seqs = events.stream()
            .map(event -> ((Number) event.get("seq")).longValue())
            .toList();
        assertThat(seqs).isSorted();

        assertThat(events.stream().map(event -> event.get("eventType")))
            .anyMatch(type -> "round_started".equals(type) || "bid_placed".equals(type));
    }
}
