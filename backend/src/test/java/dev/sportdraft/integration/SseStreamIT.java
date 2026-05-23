package dev.sportdraft.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sportdraft.bidding.boundary.AuctionRoundResponse;
import dev.sportdraft.bidding.boundary.PlaceBidRequest;
import dev.sportdraft.bidding.control.AuctionService;
import dev.sportdraft.lobby.control.LobbyService;
import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

class SseStreamIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Test
    void sseStream_receivesBidPlacedEvent() throws Exception {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        var joined = lobbyService.createRoom(user1, "SseHost");
        lobbyService.joinRoom(joined.sessionId(), user2, "SseGuest");
        UUID sessionId = joined.sessionId();

        advanceToAuctioning(sessionId, user1, user2);

        CountDownLatch eventReceived = new CountDownLatch(1);
        AtomicReference<String> capturedLine = new AtomicReference<>();

        Thread reader = new Thread(() -> readUntilBidEvent(sessionId, user1, eventReceived, capturedLine), "sse-reader");
        reader.setDaemon(true);
        reader.start();

        Thread.sleep(500);

        AuctionRoundResponse round = auctionService.getAuctionState(sessionId, user1).currentRound();
        assertThat(round).isNotNull();
        UUID bidder = round.currentTurnUserId() != null ? round.currentTurnUserId() : user1;
        IntegrationAuctionTestSupport.openBiddingOnCurrentRound(sessionStore, sessionId);
        auctionService.placeBid(
            sessionId,
            bidder,
            UUID.randomUUID().toString(),
            new PlaceBidRequest(round.roundId(), 1)
        );

        assertThat(eventReceived.await(15, SECONDS)).isTrue();
        assertThat(capturedLine.get()).contains("bid_placed");

        reader.interrupt();
    }

    private void readUntilBidEvent(
        UUID sessionId,
        UUID userId,
        CountDownLatch latch,
        AtomicReference<String> capturedLine
    ) {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(
                "http://localhost:" + port + "/api/v2/sessions/" + sessionId + "/events?token=" + userId
            );
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(20_000);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                    if (line.contains("bid_placed") || line.contains("round_started")) {
                        capturedLine.set(line);
                        latch.countDown();
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
            // Test timeout/assertion handles failure.
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void advanceToAuctioning(UUID sessionId, UUID user1, UUID user2) {
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
    }
}
