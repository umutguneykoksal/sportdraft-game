package dev.sportdraft.bidding.control;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sportdraft.bidding.entity.Card;
import dev.sportdraft.bidding.entity.Position;
import dev.sportdraft.bidding.entity.RoundStatus;
import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActivePlayer;
import dev.sportdraft.session.redis.ActiveRound;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import dev.sportdraft.session.redis.SessionMutationGuard;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuctionRoundSchedulerTest {

    @Mock
    private ActiveSessionStore sessionStore;
    @Mock
    private SessionMutationGuard mutationGuard;
    @Mock
    private CardCatalogService cardCatalog;
    @Mock
    private AuctionSettlementService settlement;
    @Mock
    private AuctionMatchTransition matchTransition;

    private AuctionRoundScheduler scheduler;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID user1 = UUID.randomUUID();
    private final UUID user2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        scheduler = new AuctionRoundScheduler(
            sessionStore,
            mutationGuard,
            cardCatalog,
            settlement,
            matchTransition
        );
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(mutationGuard).mutateVoid(any(UUID.class), any());
        when(cardCatalog.getCardMap()).thenReturn(Map.of());
    }

    @Test
    void processAuctioningSessions_closesRoundAfterGracePeriod() {
        ActiveSession session = auctioningSession();
        UUID cardId = UUID.randomUUID();
        Instant endedAt = Instant.now().minus(
            AuctionTiming.BID_GRACE_PERIOD_MS + 100, ChronoUnit.MILLIS
        );
        ActiveRound round = new ActiveRound(
            UUID.randomUUID(), cardId, endedAt.minusSeconds(30), endedAt,
            user1, RoundStatus.OPEN
        );
        session.getRounds().add(round);
        when(sessionStore.findByStatus(SessionStatus.AUCTIONING)).thenReturn(List.of(session));
        when(sessionStore.getById(sessionId)).thenReturn(session);

        scheduler.processAuctioningSessions();

        verify(settlement).closeRound(eq(round), eq(session), eq(session.getVersion()), any());
    }

    @Test
    void processAuctioningSessions_skipsRoundStillInGrace() {
        ActiveSession session = auctioningSession();
        Instant endsAt = Instant.now().plusSeconds(5);
        ActiveRound round = new ActiveRound(
            UUID.randomUUID(), UUID.randomUUID(), Instant.now(), endsAt,
            user1, RoundStatus.OPEN
        );
        session.getRounds().add(round);
        when(sessionStore.findByStatus(SessionStatus.AUCTIONING)).thenReturn(List.of(session));
        when(sessionStore.getById(sessionId)).thenReturn(session);

        scheduler.processAuctioningSessions();

        verify(settlement, never()).closeRound(any(), any(), anyLong(), any());
    }

    @Test
    void processAuctioningSessions_fullRosters_entersMatchStartPending() {
        ActiveSession session = auctioningSession();
        fillRoster(session.getPlayers().get(0));
        fillRoster(session.getPlayers().get(1));
        when(sessionStore.findByStatus(SessionStatus.AUCTIONING)).thenReturn(List.of(session));
        when(sessionStore.getById(sessionId)).thenReturn(session);

        scheduler.processAuctioningSessions();

        verify(matchTransition).enterMatchStartPending(session, session.getVersion());
    }

    @Test
    void processAuctioningSessions_optimisticLockException_isSwallowed() {
        ActiveSession session = auctioningSession();
        when(sessionStore.findByStatus(SessionStatus.AUCTIONING)).thenReturn(List.of(session));
        doThrow(new ActiveSessionStore.OptimisticLockException("stale"))
            .when(mutationGuard).mutateVoid(eq(sessionId), any());

        scheduler.processAuctioningSessions();
    }

    @Test
    void processAuctioningSessions_sessionBusyConflict_isSwallowed() {
        ActiveSession session = auctioningSession();
        when(sessionStore.findByStatus(SessionStatus.AUCTIONING)).thenReturn(List.of(session));
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Session busy, retry"))
            .when(mutationGuard).mutateVoid(eq(sessionId), any());

        scheduler.processAuctioningSessions();
    }

    @Test
    void expireMatchStartCountdowns_finalizesWhenDeadlinePassed() {
        ActiveSession session = new ActiveSession(
            sessionId, SessionStatus.MATCH_START_PENDING, Instant.now(), GameMode.DUEL
        );
        session.setMatchStartDeadlineAt(Instant.now().minusSeconds(1));
        when(sessionStore.findByStatus(SessionStatus.MATCH_START_PENDING)).thenReturn(List.of(session));
        when(sessionStore.getById(sessionId)).thenReturn(session);

        scheduler.expireMatchStartCountdowns();

        verify(matchTransition).finalizeSessionFromFullRosters(session, session.getVersion());
    }

    @Test
    void expireMatchStartCountdowns_skipsWhenDeadlineNotReached() {
        ActiveSession session = new ActiveSession(
            sessionId, SessionStatus.MATCH_START_PENDING, Instant.now(), GameMode.DUEL
        );
        session.setMatchStartDeadlineAt(Instant.now().plusSeconds(30));
        when(sessionStore.findByStatus(SessionStatus.MATCH_START_PENDING)).thenReturn(List.of(session));

        scheduler.expireMatchStartCountdowns();

        verify(mutationGuard, never()).mutateVoid(eq(sessionId), any());
    }

    private ActiveSession auctioningSession() {
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.AUCTIONING, Instant.now(), GameMode.DUEL);
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), user1, "P1", Formation.FOUR_FOUR_TWO, 100));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), user2, "P2", Formation.FOUR_FOUR_TWO, 100));
        return session;
    }

    private static void fillRoster(ActivePlayer player) {
        List<UUID> cardIds = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            cardIds.add(UUID.randomUUID());
        }
        player.setCardIds(cardIds);
    }
}
