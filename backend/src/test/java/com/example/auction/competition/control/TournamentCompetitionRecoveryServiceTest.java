package com.example.auction.competition.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auction.bidding.control.CardCatalogService;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TournamentCompetitionRecoveryServiceTest {

    @Mock
    private ActiveSessionStore sessionStore;
    @Mock
    private CardCatalogService cardCatalog;
    @Mock
    private CompetitionRepository competitionRepository;
    @Mock
    private TournamentBracketService tournamentBracketService;
    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    private TournamentCompetitionRecoveryService recoveryService;

    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        recoveryService = new TournamentCompetitionRecoveryService(
            sessionStore,
            cardCatalog,
            competitionRepository,
            tournamentBracketService,
            sseEmitterRegistry
        );
    }

    @Test
    void recoverIfMissing_alreadyExists_returnsTrue() {
        when(competitionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(new CompetitionEntity()));

        assertThat(recoveryService.recoverIfMissing(sessionId)).isTrue();
        verify(tournamentBracketService, never()).initializeTournament(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recoverIfMissing_ineligibleSession_returnsFalse() {
        when(competitionRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(sessionStore.findById(sessionId)).thenReturn(Optional.empty());

        assertThat(recoveryService.recoverIfMissing(sessionId)).isFalse();
    }
}
