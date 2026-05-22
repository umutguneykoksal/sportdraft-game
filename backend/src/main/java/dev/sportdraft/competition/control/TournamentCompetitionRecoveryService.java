package dev.sportdraft.competition.control;

import dev.sportdraft.bidding.control.CardCatalogService;
import dev.sportdraft.bidding.control.RosterSlotAssignment;
import dev.sportdraft.bidding.entity.Card;
import dev.sportdraft.bidding.entity.Position;
import dev.sportdraft.common.sse.SseEmitterRegistry;
import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActivePlayer;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import dev.sportdraft.simulation.control.DraftCompletedEvent;
import dev.sportdraft.simulation.control.SessionFinalizedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TournamentCompetitionRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(TournamentCompetitionRecoveryService.class);

    private final ActiveSessionStore sessionStore;
    private final CardCatalogService cardCatalog;
    private final CompetitionRepository competitionRepository;
    private final TournamentBracketService tournamentBracketService;
    private final SseEmitterRegistry sseEmitterRegistry;

    public TournamentCompetitionRecoveryService(
        ActiveSessionStore sessionStore,
        CardCatalogService cardCatalog,
        CompetitionRepository competitionRepository,
        TournamentBracketService tournamentBracketService,
        SseEmitterRegistry sseEmitterRegistry
    ) {
        this.sessionStore = sessionStore;
        this.cardCatalog = cardCatalog;
        this.competitionRepository = competitionRepository;
        this.tournamentBracketService = tournamentBracketService;
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    /** Re-create tournament bracket when draft finished but async init failed (e.g. JSONB insert error). */
    public boolean recoverIfMissing(UUID sessionId) {
        if (competitionRepository.findBySessionId(sessionId).isPresent()) {
            return true;
        }

        ActiveSession session = sessionStore.findById(sessionId).orElse(null);
        if (!CompetitionRecoveryTemplate.isEligibleForRecovery(session, GameMode.TOURNAMENT)) {
            return false;
        }

        try {
            DraftCompletedEvent event = buildDraftCompletedEvent(session, cardCatalog.getCardMap());
            CompetitionEntity competition = tournamentBracketService.initializeTournament(event);
            sseEmitterRegistry.broadcast(
                sessionId,
                "tournament_started",
                Map.of(
                    "sessionId", sessionId,
                    "competitionId", competition.getId()
                )
            );
            log.info("Recovered missing tournament competition for session {}", sessionId);
            return true;
        } catch (Exception ex) {
            log.error("Failed to recover tournament competition for session {}", sessionId, ex);
            return false;
        }
    }

    static DraftCompletedEvent buildDraftCompletedEvent(ActiveSession session, Map<UUID, Card> cardMap) {
        session.ensureTurnOrder();
        List<DraftCompletedEvent.DraftPlayerRoster> rosters = session.getTurnOrder().stream()
            .map(uid -> session.findPlayerByUserId(uid).orElseThrow())
            .map(p -> new DraftCompletedEvent.DraftPlayerRoster(
                p.getUserId(),
                p.getNickname(),
                p.getFormation(),
                toRatedRoster(p, cardMap)
            ))
            .toList();

        long seed = session.getMatchSeed() == null
            ? session.getId().getMostSignificantBits()
            : session.getMatchSeed();

        return new DraftCompletedEvent(
            UUID.randomUUID(),
            Instant.now(),
            session.getId(),
            session.getGameMode(),
            seed,
            rosters
        );
    }

    private static List<SessionFinalizedEvent.RatedCard> toRatedRoster(ActivePlayer player, Map<UUID, Card> cardMap) {
        return player.getCardIds().stream()
            .map(cardId -> {
                Card card = cardMap.get(cardId);
                Position assigned = RosterSlotAssignment.assignedPosition(player, cardId, cardMap);
                return new SessionFinalizedEvent.RatedCard(
                    card.getId(), assigned, card.getRating(), card.getName(), card.getAttributes()
                );
            })
            .toList();
    }
}
