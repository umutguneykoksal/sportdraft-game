package com.example.auction.bidding.control;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.auction.bidding.boundary.PlaceBidRequest;
import com.example.auction.bidding.entity.AuctionRound;
import com.example.auction.bidding.entity.AuctionRoundRepository;
import com.example.auction.bidding.entity.BidRepository;
import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.CardRepository;
import com.example.auction.bidding.entity.Position;
import com.example.auction.bidding.entity.RoundStatus;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.entity.GameSession;
import com.example.auction.session.entity.GameSessionRepository;
import com.example.auction.session.entity.SessionPlayerRepository;
import com.example.auction.session.entity.SessionStatus;
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
    private GameSessionRepository gameSessionRepository;
    @Mock
    private SessionPlayerRepository sessionPlayerRepository;
    @Mock
    private AuctionRoundRepository auctionRoundRepository;
    @Mock
    private CardRepository cardRepository;
    @Mock
    private BidRepository bidRepository;
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
            gameSessionRepository,
            sessionPlayerRepository,
            auctionRoundRepository,
            cardRepository,
            bidRepository,
            sseEmitterRegistry,
            publisher,
            meterRegistry
        );
    }

    @Test
    void startRoundRejectsWhenOpenRoundAlreadyExists() {
        UUID sessionId = UUID.randomUUID();
        GameSession session = new GameSession(sessionId, SessionStatus.AUCTIONING, Instant.now());
        Card card = new Card(UUID.randomUUID(), "A", Position.MID, 70);
        AuctionRound openRound = new AuctionRound(
            UUID.randomUUID(),
            session,
            card,
            Instant.now(),
            Instant.now().plusSeconds(10),
            UUID.randomUUID(),
            RoundStatus.OPEN
        );

        when(gameSessionRepository.lockById(sessionId)).thenReturn(Optional.of(session));
        when(auctionRoundRepository.lockBySessionIdAndStatusOrderByStartsAtDesc(sessionId, RoundStatus.OPEN))
            .thenReturn(List.of(openRound));

        assertThatThrownBy(() -> auctionService.startRound(sessionId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            });
    }

    @Test
    void placeBidRejectsWhenRoundNotInSession() {
        UUID sessionId = UUID.randomUUID();
        UUID otherSessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        GameSession otherSession = new GameSession(otherSessionId, SessionStatus.AUCTIONING, Instant.now());
        Card card = new Card(UUID.randomUUID(), "B", Position.DEF, 72);
        AuctionRound round = new AuctionRound(
            UUID.randomUUID(),
            otherSession,
            card,
            Instant.now(),
            Instant.now().plusSeconds(10),
            userId,
            RoundStatus.OPEN
        );

        when(auctionRoundRepository.lockById(round.getId())).thenReturn(Optional.of(round));

        assertThatThrownBy(() -> auctionService.placeBid(sessionId, userId, UUID.randomUUID().toString(), new PlaceBidRequest(round.getId(), 10)))
            .isInstanceOf(ResponseStatusException.class);
    }
}
