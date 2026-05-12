package com.example.auction.simulation.control;

import com.example.auction.bidding.control.CardCatalogService;
import com.example.auction.bidding.entity.Card;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
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

        MatchSimulator.Score score = matchSimulator.simulate(
            seed,
            home.getFormation(), away.getFormation(),
            home.getCardIds().stream().map(cardMap::get)
                .map(c -> new SessionFinalizedEvent.RatedCard(c.getId(), c.getPosition(), c.getRating())).toList(),
            away.getCardIds().stream().map(cardMap::get)
                .map(c -> new SessionFinalizedEvent.RatedCard(c.getId(), c.getPosition(), c.getRating())).toList()
        );

        long vBefore = session.getVersion();
        session.setHomeScore(score.homeGoals());
        session.setAwayScore(score.awayGoals());
        session.moveToFinished();
        sessionStore.saveIfVersionMatch(session, vBefore);

        publisher.publishEvent(new MatchCompletedEvent(
            UUID.randomUUID(), Instant.now(), sessionId, score.homeGoals(), score.awayGoals(), seed
        ));
        return true;
    }
}
