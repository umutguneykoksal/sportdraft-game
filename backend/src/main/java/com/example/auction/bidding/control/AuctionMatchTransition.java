package com.example.auction.bidding.control;

import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.Position;
import com.example.auction.session.control.SessionAccess;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveRound;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.simulation.control.SessionFinalizedEvent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuctionMatchTransition {

    private final ActiveSessionStore sessionStore;
    private final CardCatalogService cardCatalog;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final ApplicationEventPublisher publisher;

    public AuctionMatchTransition(
        ActiveSessionStore sessionStore,
        CardCatalogService cardCatalog,
        SseEmitterRegistry sseEmitterRegistry,
        ApplicationEventPublisher publisher
    ) {
        this.sessionStore = sessionStore;
        this.cardCatalog = cardCatalog;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.publisher = publisher;
    }

    void confirmMatchStart(ActiveSession session, UUID userId) {
        SessionAccess.requireMember(session, userId);
        long vBefore = session.getVersion();
        if (session.getStatus() != SessionStatus.MATCH_START_PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Match start is not pending");
        }
        session.registerMatchStartReady(userId);
        if (session.areBothMatchStartReady()) {
            finalizeSessionFromFullRosters(session, vBefore);
        } else {
            sessionStore.saveIfVersionMatch(session, vBefore);
            sseEmitterRegistry.broadcast(
                session.getId(), "match_start_ready",
                Map.of(
                    "sessionId", session.getId(),
                    "readyCount", session.matchStartReadyCount(),
                    "userId", userId
                )
            );
        }
    }

    void enterMatchStartPending(ActiveSession session, long vBefore) {
        if (session.getStatus() != SessionStatus.AUCTIONING) {
            return;
        }
        if (session.playerCount() < 2) {
            return;
        }
        if (!AuctionRules.isSessionRostersFull(session)) {
            return;
        }
        UUID sessionId = session.getId();
        Map<UUID, Card> cardMap = cardCatalog.getCardMap();

        session.findOpenRound().ifPresent(openRound -> {
            openRound.markPassed();
            Card roundCard = cardMap.get(openRound.getCardId());
            sseEmitterRegistry.broadcast(
                sessionId, "round_ended",
                Map.of(
                    "round", AuctionRoundResponses.toResponse(openRound, roundCard, session, cardMap),
                    "result", "PASSED",
                    "reason", "both_rosters_complete"
                )
            );
        });

        session.moveToMatchStartPending(Instant.now().plus(2, ChronoUnit.SECONDS));
        sessionStore.saveIfVersionMatch(session, vBefore);
        sseEmitterRegistry.broadcast(
            sessionId, "match_start_pending",
            Map.of("sessionId", sessionId, "deadlineAt", session.getMatchStartDeadlineAt())
        );
    }

    void finalizeSessionFromFullRosters(ActiveSession session, long vBefore) {
        if (session.getStatus() != SessionStatus.MATCH_START_PENDING) {
            return;
        }
        if (session.playerCount() < 2) {
            return;
        }
        if (!AuctionRules.isSessionRostersFull(session)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot finalize before both rosters are full");
        }
        session.moveToFinalizing(session.getId().getMostSignificantBits());
        sessionStore.saveIfVersionMatch(session, vBefore);

        Map<UUID, Card> cardMap = cardCatalog.getCardMap();
        publishSessionFinalized(session, cardMap);
        sseEmitterRegistry.broadcast(
            session.getId(), "session_finalizing",
            Map.of("sessionId", session.getId(), "reason", "rosters_complete", "seed", session.getMatchSeed())
        );
    }

    void publishSessionFinalized(ActiveSession session, Map<UUID, Card> cardMap) {
        if (session.playerCount() < 2) {
            return;
        }
        List<ActivePlayer> sorted = session.playersSortedById();
        ActivePlayer home = sorted.get(0);
        ActivePlayer away = sorted.get(1);

        List<SessionFinalizedEvent.RatedCard> homeRoster = toRatedRoster(home, cardMap);
        List<SessionFinalizedEvent.RatedCard> awayRoster = toRatedRoster(away, cardMap);

        session.moveToAwaitingResult();
        sessionStore.save(session);
        publisher.publishEvent(new SessionFinalizedEvent(
            UUID.randomUUID(), Instant.now(), session.getId(),
            session.getMatchSeed() == null ? session.getId().getMostSignificantBits() : session.getMatchSeed(),
            home.getFormation(), away.getFormation(), homeRoster, awayRoster,
            home.getUserId(), away.getUserId()
        ));
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
