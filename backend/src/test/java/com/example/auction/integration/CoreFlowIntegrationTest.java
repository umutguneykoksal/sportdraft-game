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
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class CoreFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

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

        SessionSnapshotResponse firstJoin = lobbyService.joinLobby(user1, "TestPlayer1");
        SessionSnapshotResponse secondJoin = lobbyService.joinLobby(user2, "TestPlayer2");
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
        assertThat(afterSecondReady.matchStartReadyCount()).isEqualTo(2);

        session = sessionStore.getById(sessionId);
        v = session.getVersion();
        session.setCountdownEndsAt(Instant.now().minusSeconds(1));
        sessionStore.saveIfVersionMatch(session, v);
        lobbyService.advanceTimedPhases();

        SessionSnapshotResponse auctioning = lobbyService.getSnapshot(sessionId, user1);
        assertThat(auctioning.status()).isEqualTo(SessionStatus.AUCTIONING);

        AuctionRoundResponse round = auctionService.startRound(sessionId, user1);
        AuctionRoundResponse afterBid = auctionService.placeBid(
            sessionId, user1, UUID.randomUUID().toString(),
            new PlaceBidRequest(round.roundId(), 2)
        );
        assertThat(afterBid.roundId()).isEqualTo(round.roundId());
    }
}
