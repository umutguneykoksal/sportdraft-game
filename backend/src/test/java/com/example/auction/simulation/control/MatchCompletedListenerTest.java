package com.example.auction.simulation.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.control.SessionArchiver;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.simulation.entity.MatchEvent;
import com.example.auction.simulation.entity.MatchEventType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchCompletedListenerTest {

    @Mock
    private ActiveSessionStore sessionStore;
    @Mock
    private SseEmitterRegistry sseEmitterRegistry;
    @Mock
    private SessionArchiver sessionArchiver;

    private MatchCompletedListener listener;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID homeUserId = UUID.randomUUID();
    private final UUID awayUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new MatchCompletedListener(sessionStore, sseEmitterRegistry, sessionArchiver);
    }

    @Test
    void onMatchCompleted_updatesSessionAndBroadcasts() {
        ActiveSession session = activeSession();
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(session));

        listener.onMatchCompleted(new MatchCompletedEvent(
            UUID.randomUUID(),
            Instant.now(),
            sessionId,
            2,
            1,
            55L,
            List.of(MatchEvent.forPlayer(10, MatchEventType.GOAL, homeUserId, UUID.randomUUID(), "Striker", "Goal")),
            0,
            0,
            homeUserId
        ));

        assertThat(session.getStatus()).isEqualTo(SessionStatus.FINISHED);
        assertThat(session.getHomeScore()).isEqualTo(2);
        assertThat(session.getAwayScore()).isEqualTo(1);
        assertThat(session.getMatchWinnerUserId()).isEqualTo(homeUserId);
        verify(sessionStore).saveIfVersionMatch(eq(session), anyLong());
        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("match_completed"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onMatchCompleted_broadcastsEvenWhenSessionMissing() {
        when(sessionStore.findById(sessionId)).thenReturn(Optional.empty());

        listener.onMatchCompleted(new MatchCompletedEvent(
            UUID.randomUUID(),
            Instant.now(),
            sessionId,
            1,
            0,
            55L,
            List.of(),
            0,
            0,
            homeUserId
        ));

        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("match_completed"), org.mockito.ArgumentMatchers.any());
    }

    private ActiveSession activeSession() {
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.AWAITING_RESULT, Instant.now(), GameMode.DUEL);
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), homeUserId, "Home", Formation.FOUR_FOUR_TWO, 0));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), awayUserId, "Away", Formation.FOUR_FOUR_TWO, 0));
        return session;
    }
}
