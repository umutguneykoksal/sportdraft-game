package com.example.auction.bidding.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auction.bidding.boundary.PlaceBidRequest;
import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.Position;
import com.example.auction.bidding.entity.RoundStatus;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveRound;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuctionRoundLifecycleTest {

    @Mock
    private ActiveSessionStore sessionStore;
    @Mock
    private CardCatalogService cardCatalog;
    @Mock
    private SseEmitterRegistry sseEmitterRegistry;
    @Mock
    private AuctionSettlementService settlement;
    @Mock
    private AuctionMatchTransition matchTransition;
    @Mock
    private AuctionPassHandler passHandler;

    private AuctionRoundLifecycle lifecycle;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID user1 = UUID.randomUUID();
    private final UUID user2 = UUID.randomUUID();
    private final UUID roundCardId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lifecycle = new AuctionRoundLifecycle(
            sessionStore,
            cardCatalog,
            sseEmitterRegistry,
            new SimpleMeterRegistry(),
            settlement,
            matchTransition,
            passHandler
        );
        Card roundCard = new Card(roundCardId, "Striker", Position.FWD, 85);
        lenient().when(cardCatalog.getCardMap()).thenReturn(Map.of(roundCardId, roundCard));
    }

    @Test
    void startRound_rejectsWhenOpenRoundExists() {
        ActiveSession session = auctioningSession();
        addRound(session, RoundStatus.OPEN);

        assertThatThrownBy(() -> lifecycle.startRound(session))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("An active round already exists");
            });
    }

    @Test
    void startRound_rejectsWhenNotAuctioning() {
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.FORMATION_SELECT, Instant.now(), GameMode.DUEL);
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), user1, "P1", Formation.FOUR_FOUR_TWO, 50));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), user2, "P2", Formation.FOUR_FOUR_TWO, 50));

        assertThatThrownBy(() -> lifecycle.startRound(session))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("Session is not auctioning");
            });
    }

    @Test
    void placeBid_roundNotOpen_throwsConflict() {
        ActiveSession session = auctioningSession();
        ActiveRound round = addRound(session, RoundStatus.SOLD);

        when(sessionStore.getById(sessionId)).thenReturn(session);

        assertThatThrownBy(() -> lifecycle.placeBid(
            sessionId, user1, UUID.randomUUID().toString(), new PlaceBidRequest(round.getId(), 1)
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("Round is not open");
            });
    }

    @Test
    void placeBid_roundExpired_throwsConflict() {
        ActiveSession session = auctioningSession();
        ActiveRound round = addRound(session, RoundStatus.OPEN);
        round.setEndsAt(Instant.now().minus(5, ChronoUnit.SECONDS));

        when(sessionStore.getById(sessionId)).thenReturn(session);

        assertThatThrownBy(() -> lifecycle.placeBid(
            sessionId, user1, UUID.randomUUID().toString(), new PlaceBidRequest(round.getId(), 1)
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("Round has already ended");
            });
    }

    @Test
    void placeBid_rostersComplete_throwsConflict() {
        ActiveSession session = auctioningSession();
        fillRoster(session.getPlayers().get(0));
        fillRoster(session.getPlayers().get(1));
        ActiveRound round = addRound(session, RoundStatus.OPEN);

        when(sessionStore.getById(sessionId)).thenReturn(session);

        assertThatThrownBy(() -> lifecycle.placeBid(
            sessionId, user1, UUID.randomUUID().toString(), new PlaceBidRequest(round.getId(), 1)
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("Rosters complete; confirm match start");
            });
        verify(matchTransition).enterMatchStartPending(eq(session), anyLong());
    }

    @Test
    void readyNext_withBidOnRound_throwsConflict() {
        ActiveSession session = auctioningSession();
        ActiveRound round = addRound(session, RoundStatus.OPEN);
        round.setHighestBidderId(session.getPlayers().get(0).getId());

        when(sessionStore.getById(sessionId)).thenReturn(session);

        assertThatThrownBy(() -> lifecycle.readyNext(sessionId, user1, round.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("ready-next is only allowed when there is no bid on the round");
            });
    }

    @Test
    void passRound_notYourTurn_throwsConflict() {
        ActiveSession session = auctioningSession();
        ActiveRound round = addRound(session, RoundStatus.OPEN);
        round.setCurrentTurnUserId(user2);
        round.setTurnStartedAt(Instant.now());

        when(sessionStore.getById(sessionId)).thenReturn(session);

        assertThatThrownBy(() -> lifecycle.passRound(sessionId, user1, round.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("Not your turn");
            });
    }

    @Test
    void placeBid_extendsEndsAtInAntiSnipeWindow() {
        ActiveSession session = auctioningSession();
        ActiveRound round = addRound(session, RoundStatus.OPEN);
        Instant originalEndsAt = Instant.now().plus(5, ChronoUnit.SECONDS);
        round.setEndsAt(originalEndsAt);

        when(sessionStore.getById(sessionId)).thenReturn(session);

        lifecycle.placeBid(
            sessionId, user1, UUID.randomUUID().toString(), new PlaceBidRequest(round.getId(), 1)
        );

        assertThat(round.getEndsAt()).isEqualTo(
            originalEndsAt.plus(AuctionTiming.ANTI_SNIPE_EXTENSION_SECONDS, ChronoUnit.SECONDS)
        );
        verify(sessionStore).saveIfVersionMatch(eq(session), anyLong());
    }

    private ActiveSession auctioningSession() {
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.AUCTIONING, Instant.now(), GameMode.DUEL);
        session.setTurnOrder(List.of(user1, user2));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), user1, "P1", Formation.FOUR_FOUR_TWO, 50));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), user2, "P2", Formation.FOUR_FOUR_TWO, 50));
        return session;
    }

    private ActiveRound addRound(ActiveSession session, RoundStatus status) {
        ActiveRound round = new ActiveRound(
            UUID.randomUUID(),
            roundCardId,
            Instant.now(),
            Instant.now().plus(30, ChronoUnit.SECONDS),
            user1,
            status
        );
        session.getRounds().add(round);
        return round;
    }

    private void fillRoster(ActivePlayer player) {
        List<UUID> cardIds = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            cardIds.add(UUID.randomUUID());
        }
        player.setCardIds(cardIds);
    }
}
