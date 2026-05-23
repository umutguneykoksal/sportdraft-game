package dev.sportdraft.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sportdraft.bidding.boundary.AuctionRoundResponse;
import dev.sportdraft.bidding.boundary.PlaceBidRequest;
import dev.sportdraft.bidding.control.AuctionService;
import dev.sportdraft.common.realtime.SessionEventLog;
import dev.sportdraft.lobby.boundary.SessionSnapshotResponse;
import dev.sportdraft.lobby.control.LobbyService;
import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
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

        SessionSnapshotResponse joined = lobbyService.createRoom(user1, "BidFlow1");
        lobbyService.joinRoom(joined.sessionId(), user2, "BidFlow2");
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
        IntegrationAuctionTestSupport.openBiddingOnCurrentRound(sessionStore, sessionId);
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
