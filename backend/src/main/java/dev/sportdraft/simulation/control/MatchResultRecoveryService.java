package dev.sportdraft.simulation.control;

import dev.sportdraft.bidding.control.CardCatalogService;
import dev.sportdraft.bidding.control.RosterSlotAssignment;
import dev.sportdraft.bidding.entity.Card;
import dev.sportdraft.bidding.entity.Position;
import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActivePlayer;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import dev.sportdraft.simulation.entity.SimulationResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class MatchResultRecoveryService {

    private final ActiveSessionStore sessionStore;
    private final CardCatalogService cardCatalog;
    private final MatchSimulator matchSimulator;
    private final ApplicationEventPublisher publisher;

    public MatchResultRecoveryService(
        ActiveSessionStore sessionStore,
        CardCatalogService cardCatalog,
        MatchSimulator matchSimulator,
        ApplicationEventPublisher publisher
    ) {
        this.sessionStore = sessionStore;
        this.cardCatalog = cardCatalog;
        this.matchSimulator = matchSimulator;
        this.publisher = publisher;
    }

    /**
     * If the session exists in Redis and has scores, return true.
     * If the session is in FINALIZING/AWAITING_RESULT with no scores, re-simulate.
     */
    public boolean recoverIfMissing(UUID sessionId) {
        ActiveSession session = sessionStore.findById(sessionId).orElse(null);
        if (session == null) return false;
        if (session.getHomeScore() != null) return true;
        if (session.getGameMode() != GameMode.DUEL) {
            return false;
        }

        if (session.getStatus() != SessionStatus.FINALIZING
            && session.getStatus() != SessionStatus.AWAITING_RESULT) {
            return false;
        }
        List<ActivePlayer> sorted = session.playersSortedById();
        if (sorted.size() < 2 || sorted.get(0).cardCount() < 11 || sorted.get(1).cardCount() < 11) {
            return false;
        }

        Map<UUID, Card> cardMap = cardCatalog.getCardMap();
        ActivePlayer home = sorted.get(0);
        ActivePlayer away = sorted.get(1);
        long seed = session.getMatchSeed() == null ? sessionId.getMostSignificantBits() : session.getMatchSeed();

        List<SessionFinalizedEvent.RatedCard> homeRoster = toRatedRoster(home, cardMap);
        List<SessionFinalizedEvent.RatedCard> awayRoster = toRatedRoster(away, cardMap);

        SimulationResult result = matchSimulator.simulateWithTimeline(
            seed,
            home.getFormation(), away.getFormation(),
            homeRoster, awayRoster,
            home.getUserId(), away.getUserId()
        );

        long vBefore = session.getVersion();
        session.setHomeScore(result.homeGoals());
        session.setAwayScore(result.awayGoals());
        session.setHomePenaltyScore(result.decidedOnPenalties() ? result.homePenaltyGoals() : null);
        session.setAwayPenaltyScore(result.decidedOnPenalties() ? result.awayPenaltyGoals() : null);
        session.setMatchWinnerUserId(result.resolveWinner(home.getUserId(), away.getUserId()));
        session.setMatchTimeline(result.timeline());
        session.moveToFinished();
        sessionStore.saveIfVersionMatch(session, vBefore);

        publisher.publishEvent(new MatchCompletedEvent(
            UUID.randomUUID(),
            Instant.now(),
            sessionId,
            result.homeGoals(),
            result.awayGoals(),
            seed,
            result.timeline(),
            result.homePenaltyGoals(),
            result.awayPenaltyGoals(),
            result.resolveWinner(home.getUserId(), away.getUserId())
        ));
        return true;
    }

    private List<SessionFinalizedEvent.RatedCard> toRatedRoster(ActivePlayer player, Map<UUID, Card> cardMap) {
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
