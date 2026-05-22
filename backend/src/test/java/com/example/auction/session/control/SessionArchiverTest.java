package com.example.auction.session.control;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.MatchHistoryRepository;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionArchiverTest {

    @Mock
    private ActiveSessionStore sessionStore;
    @Mock
    private MatchHistoryRepository matchHistoryRepository;

    private SessionArchiver archiver;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID homeUserId = UUID.randomUUID();
    private final UUID awayUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        archiver = new SessionArchiver(
            sessionStore,
            matchHistoryRepository,
            new ObjectMapper().registerModule(new JavaTimeModule())
        );
    }

    @Test
    void archive_persistsHistoryAndDeletesSession() {
        ActiveSession session = finishedSession();
        when(matchHistoryRepository.existsBySessionId(sessionId)).thenReturn(false);

        archiver.archive(session);

        verify(matchHistoryRepository).save(any());
        verify(sessionStore).delete(sessionId);
    }

    @Test
    void archive_skipsWhenAlreadyArchived() {
        ActiveSession session = finishedSession();
        when(matchHistoryRepository.existsBySessionId(sessionId)).thenReturn(true);

        archiver.archive(session);

        verify(matchHistoryRepository, never()).save(any());
        verify(sessionStore).delete(sessionId);
    }

    @Test
    void archive_skipsWhenTooFewPlayers() {
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.FINISHED, Instant.now(), GameMode.DUEL);
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), homeUserId, "Home", Formation.FOUR_FOUR_TWO, 0));
        when(matchHistoryRepository.existsBySessionId(sessionId)).thenReturn(false);

        archiver.archive(session);

        verify(matchHistoryRepository, never()).save(any());
        verify(sessionStore, never()).delete(sessionId);
    }

    private ActiveSession finishedSession() {
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.FINISHED, Instant.now(), GameMode.DUEL);
        session.setHomeScore(2);
        session.setAwayScore(1);
        session.setMatchSeed(42L);
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), homeUserId, "Home", Formation.FOUR_FOUR_TWO, 0));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), awayUserId, "Away", Formation.FOUR_THREE_THREE, 0));
        return session;
    }
}
