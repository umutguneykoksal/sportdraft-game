package com.example.auction.bidding.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auction.bidding.boundary.PlaceBidRequest;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveRound;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.session.redis.SessionMutationGuard;
import com.example.auction.bidding.entity.RoundStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
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
    void placeBid_delegatesToRoundLifecycle() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        PlaceBidRequest request = new PlaceBidRequest(roundId, 5);
        when(roundLifecycle.placeBid(sessionId, userId, "key-1", request)).thenReturn(null);

        auctionService.placeBid(sessionId, userId, "key-1", request);

        verify(roundLifecycle).placeBid(sessionId, userId, "key-1", request);
    }

    @Test
    void passRound_delegatesToRoundLifecycle() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        when(roundLifecycle.passRound(sessionId, userId, roundId)).thenReturn(null);

        auctionService.passRound(sessionId, userId, roundId);

        verify(roundLifecycle).passRound(sessionId, userId, roundId);
    }

    @Test
    void getAuctionState_returnsStateForMember() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.AUCTIONING, Instant.now());
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), userId, "P1", Formation.FOUR_FOUR_TWO, 100));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), UUID.randomUUID(), "P2", Formation.FOUR_FOUR_TWO, 100));
        when(sessionStore.getById(sessionId)).thenReturn(session);
        when(cardCatalog.getCardMap()).thenReturn(Map.of());

        var state = auctionService.getAuctionState(sessionId, userId);

        assertThat(state.version()).isEqualTo(session.getVersion());
        assertThat(state.myFormation()).isEqualTo(Formation.FOUR_FOUR_TWO.name());
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
    void confirmMatchStart_delegatesToMatchTransition() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.MATCH_START_PENDING, Instant.now());
        when(sessionStore.getById(sessionId)).thenReturn(session);

        auctionService.confirmMatchStart(sessionId, userId);

        verify(matchTransition).confirmMatchStart(session, userId);
    }

    @Test
    void ensureOpenRound_delegatesToRoundLifecycle() {
        UUID sessionId = UUID.randomUUID();
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.AUCTIONING, Instant.now());
        when(sessionStore.getById(sessionId)).thenReturn(session);

        auctionService.ensureOpenRound(sessionId);

        verify(roundLifecycle).ensureOpenRound(session);
    }

    @Test
    void buildSpectateSnapshot_returnsHomeAndAwayTeams() {
        UUID sessionId = UUID.randomUUID();
        UUID homeId = UUID.randomUUID();
        UUID awayId = UUID.randomUUID();
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.AUCTIONING, Instant.now(), GameMode.DUEL);
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), homeId, "Home", Formation.FOUR_FOUR_TWO, 80));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), awayId, "Away", Formation.FOUR_THREE_THREE, 70));
        session.setHomeScore(2);
        session.setAwayScore(1);
        when(cardCatalog.getCardMap()).thenReturn(Map.of());

        AuctionService.SpectateSnapshot snapshot = auctionService.buildSpectateSnapshot(session);

        assertThat(List.of(snapshot.homeNickname(), snapshot.awayNickname()))
            .containsExactlyInAnyOrder("Home", "Away");
        assertThat(snapshot.homeScore()).isEqualTo(2);
        assertThat(snapshot.awayScore()).isEqualTo(1);
    }

    @Test
    void getAuctionState_tournamentModeIncludesPlayerViews() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.AUCTIONING, Instant.now(), GameMode.TOURNAMENT);
        session.setTurnOrder(List.of(userId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        for (int i = 0; i < 4; i++) {
            session.getPlayers().add(new ActivePlayer(
                UUID.randomUUID(), session.getTurnOrder().get(i), "P" + i, Formation.FOUR_FOUR_TWO, 100
            ));
        }
        when(sessionStore.getById(sessionId)).thenReturn(session);
        when(cardCatalog.getCardMap()).thenReturn(Map.of());

        var state = auctionService.getAuctionState(sessionId, userId);

        assertThat(state.players()).hasSize(4);
    }

    @Test
    void buildTeamSnapshot_returnsBothTeams() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID opponentId = UUID.randomUUID();
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.AUCTIONING, Instant.now());
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), userId, "Me", Formation.FOUR_FOUR_TWO, 100));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), opponentId, "Opp", Formation.FOUR_THREE_THREE, 100));
        when(cardCatalog.getCardMap()).thenReturn(Map.of());

        AuctionService.TeamSnapshot teams = auctionService.buildTeamSnapshot(session, userId);

        assertThat(teams.myFormation()).isEqualTo(Formation.FOUR_FOUR_TWO.name());
        assertThat(teams.opponentFormation()).isEqualTo(Formation.FOUR_THREE_THREE.name());
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
