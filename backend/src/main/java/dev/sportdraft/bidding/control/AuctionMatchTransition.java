package dev.sportdraft.bidding.control;

import dev.sportdraft.bidding.entity.Card;
import dev.sportdraft.bidding.entity.Position;
import dev.sportdraft.session.control.SessionAccess;
import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.simulation.control.DraftCompletedEvent;
import dev.sportdraft.session.redis.ActivePlayer;
import dev.sportdraft.session.redis.ActiveRound;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import dev.sportdraft.common.sse.SseEmitterRegistry;
import dev.sportdraft.simulation.control.SessionFinalizedEvent;
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
        if (session.areAllMatchStartReady()) {
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
        if (!AuctionRules.hasFullLobby(session)) {
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
        if (!AuctionRules.hasFullLobby(session)) {
            return;
        }
        if (!AuctionRules.isSessionRostersFull(session)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot finalize before all rosters are full");
        }
        session.moveToFinalizing(session.getId().getMostSignificantBits());
        sessionStore.saveIfVersionMatch(session, vBefore);

        Map<UUID, Card> cardMap = cardCatalog.getCardMap();
        if (session.getGameMode() == GameMode.DUEL) {
            publishSessionFinalized(session, cardMap);
        } else {
            publishDraftCompleted(session, cardMap);
        }
        sseEmitterRegistry.broadcast(
            session.getId(), "session_finalizing",
            Map.of(
                "sessionId", session.getId(),
                "reason", "rosters_complete",
                "seed", session.getMatchSeed(),
                "gameMode", session.getGameMode().name()
            )
        );
    }

    void publishSessionFinalized(ActiveSession session, Map<UUID, Card> cardMap) {
        if (!AuctionRules.hasFullLobby(session) || session.getGameMode() != GameMode.DUEL) {
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

    void publishDraftCompleted(ActiveSession session, Map<UUID, Card> cardMap) {
        if (!AuctionRules.hasFullLobby(session)) {
            return;
        }
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

        session.moveToAwaitingResult();
        sessionStore.save(session);
        publisher.publishEvent(new DraftCompletedEvent(
            UUID.randomUUID(),
            Instant.now(),
            session.getId(),
            session.getGameMode(),
            session.getMatchSeed() == null ? session.getId().getMostSignificantBits() : session.getMatchSeed(),
            rosters
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
