package com.example.auction.bidding.control;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.auction.bidding.boundary.PlaceBidRequest;
import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.CardRepository;
import com.example.auction.bidding.entity.Position;
import com.example.auction.bidding.entity.RoundStatus;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveRound;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @Mock
    private ActiveSessionStore sessionStore;
    @Mock
    private CardRepository cardRepository;
    @Mock
    private SseEmitterRegistry sseEmitterRegistry;
    @Mock
    private ApplicationEventPublisher publisher;
    @Mock
    private MeterRegistry meterRegistry;

    private AuctionService auctionService;

    @BeforeEach
    void setUp() {
        auctionService = new AuctionService(
            sessionStore,
            cardRepository,
            sseEmitterRegistry,
            publisher,
            meterRegistry
        );
    }

    @Test
    void startRoundRejectsWhenOpenRoundAlreadyExists() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.AUCTIONING, Instant.now());
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), UUID.randomUUID(), "P1", Formation.FOUR_FOUR_TWO, 100));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), UUID.randomUUID(), "P2", Formation.FOUR_FOUR_TWO, 100));

        ActiveRound openRound = new ActiveRound(
            UUID.randomUUID(), UUID.randomUUID(),
            Instant.now(), Instant.now().plusSeconds(10),
            UUID.randomUUID(), RoundStatus.OPEN
        );
        session.getRounds().add(openRound);

        when(sessionStore.getById(sessionId)).thenReturn(session);

        assertThatThrownBy(() -> auctionService.startRound(sessionId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            });
    }

    @Test
    void startRoundRejectsWhenNotAuctioning() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.WAITING, Instant.now());
        when(sessionStore.getById(sessionId)).thenReturn(session);

        assertThatThrownBy(() -> auctionService.startRound(sessionId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            });
    }
}
