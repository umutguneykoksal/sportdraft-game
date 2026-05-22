package dev.sportdraft.competition.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sportdraft.bidding.control.CardCatalogService;
import dev.sportdraft.common.sse.SseEmitterRegistry;
import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.session.redis.ActiveSessionStore;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeagueCompetitionRecoveryServiceTest {

    @Mock
    private ActiveSessionStore sessionStore;
    @Mock
    private CardCatalogService cardCatalog;
    @Mock
    private CompetitionRepository competitionRepository;
    @Mock
    private LeagueFixtureService leagueFixtureService;
    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    private LeagueCompetitionRecoveryService recoveryService;

    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        recoveryService = new LeagueCompetitionRecoveryService(
            sessionStore,
            cardCatalog,
            competitionRepository,
            leagueFixtureService,
            sseEmitterRegistry
        );
    }

    @Test
    void recoverIfMissing_alreadyExists_returnsTrue() {
        when(competitionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(new CompetitionEntity()));

        assertThat(recoveryService.recoverIfMissing(sessionId)).isTrue();
        verify(leagueFixtureService, never()).initializeLeague(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recoverIfMissing_missingSession_returnsFalse() {
        when(competitionRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(sessionStore.findById(sessionId)).thenReturn(Optional.empty());

        assertThat(recoveryService.recoverIfMissing(sessionId)).isFalse();
    }
}
