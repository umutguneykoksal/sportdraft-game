package com.example.auction.session.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auction.bidding.control.AuctionService;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.MatchHistory;
import com.example.auction.session.entity.MatchHistoryRepository;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.simulation.control.MatchResultRecoveryService;
import com.example.auction.simulation.entity.MatchEvent;
import com.example.auction.simulation.entity.MatchEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SessionReadServiceTest {

    @Mock
    private ActiveSessionStore sessionStore;
    @Mock
    private MatchHistoryRepository matchHistoryRepository;
    @Mock
    private MatchResultRecoveryService matchResultRecoveryService;
    @Mock
    private AuctionService auctionService;
    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    private ObjectMapper objectMapper;
    private SessionReadService sessionReadService;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID homeUserId = UUID.randomUUID();
    private final UUID awayUserId = UUID.randomUUID();
    private final UUID outsiderUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        sessionReadService = new SessionReadService(
            sessionStore,
            matchHistoryRepository,
            matchResultRecoveryService,
            auctionService,
            sseEmitterRegistry,
            objectMapper
        );
    }

    @Test
    void getMatchResult_nonMemberFromArchive_throws403() throws Exception {
        MatchHistory history = archivedMatchHistory();
        when(sessionStore.findById(sessionId)).thenReturn(Optional.empty());
        when(matchHistoryRepository.findBySessionId(sessionId)).thenReturn(Optional.of(history));

        assertThatThrownBy(() -> sessionReadService.getMatchResult(sessionId, outsiderUserId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(rse.getReason()).isEqualTo("Player not found in session");
            });
    }

    @Test
    void getMatchResult_unknownSession_returns404() {
        when(sessionStore.findById(sessionId)).thenReturn(Optional.empty());
        when(matchHistoryRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        ResponseEntity<?> response = sessionReadService.getMatchResult(sessionId, homeUserId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getMatchTimeline_nonMember_throws403() throws Exception {
        MatchHistory history = archivedMatchHistory();
        when(sessionStore.findById(sessionId)).thenReturn(Optional.empty());
        when(matchHistoryRepository.findBySessionId(sessionId)).thenReturn(Optional.of(history));

        assertThatThrownBy(() -> sessionReadService.getMatchTimeline(sessionId, outsiderUserId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(rse.getReason()).isEqualTo("Player not found in session");
            });
    }

    @Test
    void getMatchTimeline_memberFromActiveSession_returnsTimeline() {
        ActiveSession session = activeSessionWithTimeline();
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(session));

        ResponseEntity<?> response = sessionReadService.getMatchTimeline(sessionId, homeUserId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<MatchEvent> body = (List<MatchEvent>) response.getBody();
        assertThat(body).hasSize(1);
    }

    @Test
    void getMatchResult_activeSession_triggersRecovery() {
        ActiveSession session = activeSessionWithTimeline();
        session.setStatus(SessionStatus.FINALIZING);
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(session));
        when(matchResultRecoveryService.recoverIfMissing(sessionId)).thenReturn(true);
        when(auctionService.buildTeamSnapshot(session, homeUserId)).thenReturn(
            new AuctionService.TeamSnapshot(List.of(), List.of(), "4-4-2", "4-4-2")
        );

        ResponseEntity<?> response = sessionReadService.getMatchResult(sessionId, homeUserId);

        verify(matchResultRecoveryService).recoverIfMissing(sessionId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void spectate_delegatesToAuctionService() {
        ActiveSession session = activeSessionWithTimeline();
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(session));
        when(auctionService.buildSpectateSnapshot(session)).thenReturn(
            new AuctionService.SpectateSnapshot(
                SessionStatus.AUCTIONING.name(),
                null,
                List.of(), List.of(),
                Formation.FOUR_FOUR_TWO.name(), Formation.FOUR_FOUR_TWO.name(),
                homeUserId, awayUserId,
                "Home", "Away",
                50L, 50L,
                1, 0
            )
        );

        var response = sessionReadService.spectate(sessionId);

        assertThat(response.status()).isEqualTo(SessionStatus.AUCTIONING.name());
        assertThat(response.homeNickname()).isEqualTo("Home");
    }

    @Test
    void getMatchResult_memberFromArchive_returnsScores() throws Exception {
        MatchHistory history = archivedMatchHistory();
        when(sessionStore.findById(sessionId)).thenReturn(Optional.empty());
        when(matchHistoryRepository.findBySessionId(sessionId)).thenReturn(Optional.of(history));
        when(auctionService.buildTeamSnapshot(org.mockito.ArgumentMatchers.any(), eq(homeUserId))).thenReturn(
            new AuctionService.TeamSnapshot(List.of(), List.of(), "4-4-2", "4-3-3")
        );

        ResponseEntity<?> response = sessionReadService.getMatchResult(sessionId, homeUserId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getMatchResult_corruptArchive_returnsScoresOnly() throws Exception {
        MatchHistory history = new MatchHistory(
            UUID.randomUUID(),
            sessionId,
            homeUserId,
            awayUserId,
            2,
            1,
            Formation.FOUR_FOUR_TWO.name(),
            Formation.FOUR_THREE_THREE.name(),
            123L,
            Instant.now(),
            "{not-json"
        );
        when(sessionStore.findById(sessionId)).thenReturn(Optional.empty());
        when(matchHistoryRepository.findBySessionId(sessionId)).thenReturn(Optional.of(history));

        ResponseEntity<?> response = sessionReadService.getMatchResult(sessionId, homeUserId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getMatchResult_finishedActiveSession_includesStats() {
        ActiveSession session = activeSessionWithTimeline();
        session.setStatus(SessionStatus.FINISHED);
        session.setMatchWinnerUserId(homeUserId);
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(session));
        when(auctionService.buildTeamSnapshot(session, homeUserId)).thenReturn(
            new AuctionService.TeamSnapshot(List.of(), List.of(), "4-4-2", "4-4-2")
        );

        ResponseEntity<?> response = sessionReadService.getMatchResult(sessionId, homeUserId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getMatchTimeline_memberFromArchive_returnsTimeline() throws Exception {
        MatchHistory history = archivedMatchHistory();
        when(sessionStore.findById(sessionId)).thenReturn(Optional.empty());
        when(matchHistoryRepository.findBySessionId(sessionId)).thenReturn(Optional.of(history));

        ResponseEntity<?> response = sessionReadService.getMatchTimeline(sessionId, homeUserId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getSpectateMatchTimeline_activeSession_returnsTimeline() {
        ActiveSession session = activeSessionWithTimeline();
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(session));

        ResponseEntity<?> response = sessionReadService.getSpectateMatchTimeline(sessionId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<MatchEvent> body = (List<MatchEvent>) response.getBody();
        assertThat(body).hasSize(1);
    }

    @Test
    void getSpectateMatchTimeline_fromArchive_returnsTimeline() throws Exception {
        MatchHistory history = archivedMatchHistory();
        when(sessionStore.findById(sessionId)).thenReturn(Optional.empty());
        when(matchHistoryRepository.findBySessionId(sessionId)).thenReturn(Optional.of(history));

        ResponseEntity<?> response = sessionReadService.getSpectateMatchTimeline(sessionId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void spectate_unknownSession_throws404() {
        when(sessionStore.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionReadService.spectate(sessionId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void recordPing_delegatesToSseRegistry() {
        sessionReadService.recordPing(sessionId, homeUserId);
        verify(sseEmitterRegistry).recordPlayerPing(sessionId, homeUserId);
    }

    private ActiveSession activeSessionWithTimeline() {
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.FINISHED, Instant.now(), GameMode.DUEL);
        session.setHomeScore(1);
        session.setAwayScore(0);
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), homeUserId, "Home", Formation.FOUR_FOUR_TWO, 0));
        session.getPlayers().add(new ActivePlayer(UUID.randomUUID(), awayUserId, "Away", Formation.FOUR_FOUR_TWO, 0));
        session.setMatchTimeline(List.of(
            MatchEvent.forPlayer(10, MatchEventType.GOAL, homeUserId, UUID.randomUUID(), "Striker", "Goal")
        ));
        return session;
    }

    private MatchHistory archivedMatchHistory() throws Exception {
        ActiveSession archived = new ActiveSession(sessionId, SessionStatus.FINISHED, Instant.now(), GameMode.DUEL);
        archived.setHomeScore(2);
        archived.setAwayScore(1);
        archived.getPlayers().add(new ActivePlayer(UUID.randomUUID(), homeUserId, "Home", Formation.FOUR_FOUR_TWO, 0));
        archived.getPlayers().add(new ActivePlayer(UUID.randomUUID(), awayUserId, "Away", Formation.FOUR_FOUR_TWO, 0));
        archived.setMatchTimeline(List.of());

        String replayData = objectMapper.writeValueAsString(archived);
        return new MatchHistory(
            UUID.randomUUID(),
            sessionId,
            homeUserId,
            awayUserId,
            2,
            1,
            Formation.FOUR_FOUR_TWO.name(),
            Formation.FOUR_FOUR_TWO.name(),
            123L,
            Instant.now(),
            replayData
        );
    }
}
