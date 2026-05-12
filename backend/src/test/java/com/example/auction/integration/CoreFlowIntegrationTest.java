package com.example.auction.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auction.bidding.boundary.AuctionRoundResponse;
import com.example.auction.bidding.boundary.PlaceBidRequest;
import com.example.auction.bidding.control.AuctionService;
import com.example.auction.lobby.boundary.SessionSnapshotResponse;
import com.example.auction.lobby.control.LobbyService;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.simulation.control.MatchCompletedEvent;
import com.example.auction.simulation.control.MatchCompletedListener;
import com.example.auction.simulation.control.MatchSimulationListener;
import com.example.auction.simulation.control.SessionFinalizedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Disabled("Requires local Docker daemon for Testcontainers integration run")
class CoreFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> 6379);
    }

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Autowired
    private MatchSimulationListener matchSimulationListener;

    @Autowired
    private MatchCompletedListener matchCompletedListener;

    @Test
    void lobbyAuctionAndSimulationCoreFlowWorks() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        SessionSnapshotResponse firstJoin = lobbyService.joinLobby(user1);
        SessionSnapshotResponse secondJoin = lobbyService.joinLobby(user2);
        UUID sessionId = secondJoin.sessionId();
        assertThat(firstJoin.sessionId()).isEqualTo(sessionId);

        // Fast-forward countdown
        ActiveSession session = sessionStore.getById(sessionId);
        long v = session.getVersion();
        session.moveToCountdown(Instant.now().minusSeconds(1));
        sessionStore.saveIfVersionMatch(session, v);
        lobbyService.advanceTimedPhases();

        SessionSnapshotResponse afterFormationStart = lobbyService.getSnapshot(sessionId, user1);
        assertThat(afterFormationStart.status()).isEqualTo(SessionStatus.FORMATION_SELECT);

        lobbyService.selectFormation(sessionId, user1, Formation.FOUR_FOUR_TWO);
        SessionSnapshotResponse auctioning = lobbyService.selectFormation(sessionId, user2, Formation.FOUR_THREE_THREE);
        assertThat(auctioning.status()).isEqualTo(SessionStatus.AUCTIONING);

        AuctionRoundResponse round = auctionService.startRound(sessionId);
        auctionService.placeBid(
            sessionId, user1, UUID.randomUUID().toString(),
            new PlaceBidRequest(round.roundId(), 2)
        );
    }
}
