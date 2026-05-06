package com.example.auction.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auction.bidding.boundary.AuctionRoundResponse;
import com.example.auction.bidding.boundary.PlaceBidRequest;
import com.example.auction.bidding.control.AuctionService;
import com.example.auction.bidding.entity.AuctionRound;
import com.example.auction.bidding.entity.AuctionRoundRepository;
import com.example.auction.lobby.boundary.SessionSnapshotResponse;
import com.example.auction.lobby.control.LobbyService;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameSession;
import com.example.auction.session.entity.GameSessionRepository;
import com.example.auction.session.entity.SessionPlayer;
import com.example.auction.session.entity.SessionPlayerRepository;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.simulation.control.MatchCompletedEvent;
import com.example.auction.simulation.control.MatchCompletedListener;
import com.example.auction.simulation.control.MatchSimulationListener;
import com.example.auction.simulation.control.SessionFinalizedEvent;
import com.example.auction.simulation.entity.MatchResult;
import com.example.auction.simulation.entity.MatchResultRepository;
import java.time.Instant;
import java.util.List;
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
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private SessionPlayerRepository sessionPlayerRepository;

    @Autowired
    private AuctionRoundRepository auctionRoundRepository;

    @Autowired
    private MatchSimulationListener matchSimulationListener;

    @Autowired
    private MatchCompletedListener matchCompletedListener;

    @Autowired
    private MatchResultRepository matchResultRepository;

    @Test
    void lobbyAuctionAndSimulationCoreFlowWorks() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        SessionSnapshotResponse firstJoin = lobbyService.joinLobby(user1);
        SessionSnapshotResponse secondJoin = lobbyService.joinLobby(user2);
        UUID sessionId = secondJoin.sessionId();
        assertThat(firstJoin.sessionId()).isEqualTo(sessionId);

        GameSession session = gameSessionRepository.findById(sessionId).orElseThrow();
        session.moveToCountdown(Instant.now().minusSeconds(1));
        gameSessionRepository.save(session);
        lobbyService.advanceTimedPhases();

        SessionSnapshotResponse afterFormationStart = lobbyService.getSnapshot(sessionId, user1);
        assertThat(afterFormationStart.status()).isEqualTo(SessionStatus.FORMATION_SELECT);

        lobbyService.selectFormation(sessionId, user1, Formation.FOUR_FOUR_TWO);
        SessionSnapshotResponse auctioning = lobbyService.selectFormation(sessionId, user2, Formation.FOUR_THREE_THREE);
        assertThat(auctioning.status()).isEqualTo(SessionStatus.AUCTIONING);

        AuctionRoundResponse round = auctionService.startRound(sessionId);
        auctionService.placeBid(
            sessionId,
            user1,
            UUID.randomUUID().toString(),
            new PlaceBidRequest(round.roundId(), 200)
        );

        AuctionRound persistedRound = auctionRoundRepository.findById(round.roundId()).orElseThrow();
        persistedRound.registerBid(
            persistedRound.getHighestBidderId(),
            persistedRound.getHighestBidCents(),
            Instant.now().minusSeconds(1)
        );
        auctionRoundRepository.save(persistedRound);
        auctionService.closeExpiredRounds();

        List<SessionPlayer> players = sessionPlayerRepository.findBySession_Id(sessionId);
        assertThat(players).hasSize(2);

        GameSession finalizingSession = gameSessionRepository.findById(sessionId).orElseThrow();
        finalizingSession.moveToFinalizing(finalizingSession.getId().getMostSignificantBits());
        gameSessionRepository.save(finalizingSession);

        SessionPlayer home = players.get(0);
        SessionPlayer away = players.get(1);
        SessionFinalizedEvent event = new SessionFinalizedEvent(
            UUID.randomUUID(),
            Instant.now(),
            sessionId,
            finalizingSession.getMatchSeed(),
            home.getFormation(),
            away.getFormation(),
            home.getCards().stream().map(card -> new SessionFinalizedEvent.RatedCard(card.getId(), card.getPosition(), card.getRating())).toList(),
            away.getCards().stream().map(card -> new SessionFinalizedEvent.RatedCard(card.getId(), card.getPosition(), card.getRating())).toList()
        );

        matchSimulationListener.onSessionFinalized(event);

        MatchResult matchResult = matchResultRepository.findBySession_Id(sessionId).orElseThrow();
        assertThat(matchResult.getSeed()).isEqualTo(finalizingSession.getMatchSeed());

        matchCompletedListener.onMatchCompleted(
            new MatchCompletedEvent(
                UUID.randomUUID(),
                Instant.now(),
                sessionId,
                matchResult.getHomeScore(),
                matchResult.getAwayScore(),
                matchResult.getSeed()
            )
        );

        GameSession finished = gameSessionRepository.findById(sessionId).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(SessionStatus.FINISHED);
    }
}
