package com.example.auction.bidding.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveRound;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.session.redis.SessionMutationGuard;
import com.example.auction.bidding.entity.RoundStatus;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @Mock
    private ActiveSessionStore sessionStore;
    @Mock
    private SessionMutationGuard mutationGuard;
    @Mock
    private CardCatalogService cardCatalog;
    @Mock
    private AuctionRoundLifecycle roundLifecycle;
    @Mock
    private AuctionMatchTransition matchTransition;

    private AuctionService auctionService;

    @BeforeEach
    void setUp() {
        when(mutationGuard.mutate(any(UUID.class), any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(1);
            return action.get();
        });
        lenient().doAnswer(invocation -> {
            Runnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(mutationGuard).mutateVoid(any(UUID.class), any());
        auctionService = new AuctionService(
            sessionStore,
            mutationGuard,
            cardCatalog,
            roundLifecycle,
            matchTransition
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
        when(roundLifecycle.startRound(session))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "An active round already exists"));

        assertThatThrownBy(() -> auctionService.startRound(sessionId, session.getPlayers().get(0).getUserId()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            });
    }

    @Test
    void startRoundRejectsWhenNotAuctioning() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.WAITING, Instant.now());
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), userId, "P1", Formation.FOUR_FOUR_TWO, 100));
        when(sessionStore.getById(sessionId)).thenReturn(session);
        when(roundLifecycle.startRound(session))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Session is not auctioning"));

        assertThatThrownBy(() -> auctionService.startRound(sessionId, userId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            });
    }

    @Test
    void startRoundRejectsNonMember() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.AUCTIONING, Instant.now());
        when(sessionStore.getById(sessionId)).thenReturn(session);

        assertThatThrownBy(() -> auctionService.startRound(sessionId, UUID.randomUUID()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            });
    }
}
