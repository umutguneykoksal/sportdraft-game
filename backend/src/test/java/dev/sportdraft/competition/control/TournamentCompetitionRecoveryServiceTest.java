package dev.sportdraft.competition.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sportdraft.bidding.control.CardCatalogService;
import dev.sportdraft.bidding.entity.Card;
import dev.sportdraft.bidding.entity.Position;
import dev.sportdraft.common.sse.SseEmitterRegistry;
import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActivePlayer;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    void recoverIfMissing_eligibleSession_recoversTournament() {
        ActiveSession session = eligibleTournamentSession();
        CompetitionEntity competition = new CompetitionEntity();
        competition.setId(UUID.randomUUID());

        when(competitionRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(sessionStore.findById(sessionId)).thenReturn(Optional.of(session));
        when(cardCatalog.getCardMap()).thenReturn(cardMapFor(session));
        when(tournamentBracketService.initializeTournament(org.mockito.ArgumentMatchers.any())).thenReturn(competition);

        assertThat(recoveryService.recoverIfMissing(sessionId)).isTrue();
        verify(sseEmitterRegistry).broadcast(eq(sessionId), eq("tournament_started"), org.mockito.ArgumentMatchers.any());
    }

    private ActiveSession eligibleTournamentSession() {
        ActiveSession session = new ActiveSession(
            sessionId, SessionStatus.AWAITING_RESULT, Instant.now(), GameMode.TOURNAMENT
        );
        UUID u3 = UUID.randomUUID();
        UUID u4 = UUID.randomUUID();
        session.setTurnOrder(List.of(UUID.randomUUID(), UUID.randomUUID(), u3, u4));
        for (int i = 0; i < 4; i++) {
            ActivePlayer player = new ActivePlayer(
                UUID.randomUUID(),
                session.getTurnOrder().get(i),
                "P" + i,
                Formation.FOUR_FOUR_TWO,
                0
            );
            List<UUID> cards = new ArrayList<>();
            for (int j = 0; j < 11; j++) {
                cards.add(UUID.randomUUID());
            }
            player.setCardIds(cards);
            session.getPlayers().add(player);
        }
        return session;
    }

    private Map<UUID, Card> cardMapFor(ActiveSession session) {
        Map<UUID, Card> cardMap = new HashMap<>();
        session.getPlayers().forEach(player ->
            player.getCardIds().forEach(cardId ->
                cardMap.put(cardId, new Card(cardId, "Player", Position.FWD, 80))
            )
        );
        return cardMap;
    }

    @Test
    void recoverIfMissing_ineligibleSession_returnsFalse() {
        when(competitionRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(sessionStore.findById(sessionId)).thenReturn(Optional.empty());

        assertThat(recoveryService.recoverIfMissing(sessionId)).isFalse();
    }
}
