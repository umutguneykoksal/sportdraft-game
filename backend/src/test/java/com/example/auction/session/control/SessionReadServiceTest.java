package com.example.auction.session.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
