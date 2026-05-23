package dev.sportdraft.bidding.control;

import dev.sportdraft.bidding.entity.Card;
import dev.sportdraft.bidding.entity.RoundStatus;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActivePlayer;
import dev.sportdraft.session.redis.ActiveRound;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import dev.sportdraft.common.sse.SseEmitterRegistry;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuctionSettlementService {

    private static final Logger log = LoggerFactory.getLogger(AuctionSettlementService.class);

    private final ActiveSessionStore sessionStore;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final AuctionMatchTransition matchTransition;
    private final AuctionRoundLifecycle roundLifecycle;

    public AuctionSettlementService(
        ActiveSessionStore sessionStore,
        SseEmitterRegistry sseEmitterRegistry,
        AuctionMatchTransition matchTransition,
        @Lazy AuctionRoundLifecycle roundLifecycle
    ) {
        this.sessionStore = sessionStore;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.matchTransition = matchTransition;
        this.roundLifecycle = roundLifecycle;
    }

    void closeRound(ActiveRound round, ActiveSession session, long vBefore, Map<UUID, Card> cardMap) {
        UUID sessionId = session.getId();
        if (round.getStatus() != RoundStatus.OPEN || session.getStatus() != SessionStatus.AUCTIONING) {
            return;
        }
        broadcastRoundResolving(sessionId, round.getId());
        Card roundCard = cardMap.get(round.getCardId());

        if (round.getHighestBidderId() == null) {
            round.markPassed();
            sessionStore.saveIfVersionMatch(session, vBefore);
            sseEmitterRegistry.broadcast(
                sessionId, "round_ended",
                Map.of("round", AuctionRoundResponses.toResponse(round, roundCard, session, cardMap), "result", "PASSED")
            );
            maybeFinalizeSession(session);
            roundLifecycle.maybeStartNextRound(session);
            logRoundClosed(sessionId, round.getId(), "PASSED", null);
            return;
        }

        ActivePlayer winner = session.getPlayers().stream()
            .filter(p -> p.getId().equals(round.getHighestBidderId()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Winner not found"));

        if (winner.isRosterFull()) {
            round.markPassed();
            sessionStore.saveIfVersionMatch(session, vBefore);
            sseEmitterRegistry.broadcast(
                sessionId, "round_ended",
                Map.of(
                    "round", AuctionRoundResponses.toResponse(round, roundCard, session, cardMap),
                    "result", "PASSED",
                    "reason", "winner_roster_full"
                )
            );
            maybeFinalizeSession(session);
            roundLifecycle.maybeStartNextRound(session);
            logRoundClosed(sessionId, round.getId(), "PASSED", "winner_roster_full");
            return;
        }
        if (!RosterSlotAssignment.canFillCard(winner, roundCard, cardMap)) {
            round.markPassed();
            sessionStore.saveIfVersionMatch(session, vBefore);
            sseEmitterRegistry.broadcast(
                sessionId, "round_ended",
                Map.of(
                    "round", AuctionRoundResponses.toResponse(round, roundCard, session, cardMap),
                    "result", "PASSED",
                    "reason", "winner_position_full"
                )
            );
            maybeFinalizeSession(session);
            roundLifecycle.maybeStartNextRound(session);
            logRoundClosed(sessionId, round.getId(), "PASSED", "winner_position_full");
            return;
        }

        AuctionRules.awardCardToWinner(winner, roundCard, round.getHighestBidCents(), cardMap);
        round.markSold();
        sessionStore.saveIfVersionMatch(session, vBefore);

        sseEmitterRegistry.broadcast(
            sessionId, "round_ended",
            Map.of(
                "round", AuctionRoundResponses.toResponse(round, roundCard, session, cardMap),
                "result", "SOLD",
                "winnerUserId", winner.getUserId()
            )
        );
        maybeFinalizeSession(session);
        roundLifecycle.maybeStartNextRound(session);
        logRoundClosed(sessionId, round.getId(), "SOLD", "amountCents=" + round.getHighestBidCents());
    }

    void settleRoundInline(ActiveRound round, ActiveSession session, Map<UUID, Card> cardMap) {
        Card roundCard = cardMap.get(round.getCardId());
        ActivePlayer winner = session.getPlayers().stream()
            .filter(p -> p.getId().equals(round.getHighestBidderId()))
            .findFirst().orElse(null);

        if (winner == null || winner.isRosterFull()
            || !RosterSlotAssignment.canFillCard(winner, roundCard, cardMap)) {
            round.markPassed();
        } else {
            AuctionRules.awardCardToWinner(winner, roundCard, round.getHighestBidCents(), cardMap);
            round.markSold();
        }
    }

    void finalizeSettledRound(
        ActiveRound round,
        ActiveSession session,
        long vBefore,
        Map<UUID, Card> cardMap,
        boolean chainNextRound
    ) {
        UUID sessionId = session.getId();
        broadcastRoundResolving(sessionId, round.getId());
        sessionStore.saveIfVersionMatch(session, vBefore);
        broadcastSettledRound(round, session, cardMap);
        maybeFinalizeSession(session);
        if (chainNextRound) {
            roundLifecycle.maybeStartNextRound(session);
        }
    }

    void broadcastSettledRound(ActiveRound round, ActiveSession session, Map<UUID, Card> cardMap) {
        if (round.getStatus() == RoundStatus.OPEN) {
            return;
        }

        Card roundCard = cardMap.get(round.getCardId());
        UUID sessionId = session.getId();

        if (round.getStatus() == RoundStatus.PASSED) {
            String reason = round.getHighestBidderId() != null ? "winner_cannot_take" : "both_passed";
            sseEmitterRegistry.broadcast(
                sessionId, "round_ended",
                Map.of(
                    "round", AuctionRoundResponses.toResponse(round, roundCard, session, cardMap),
                    "result", "PASSED",
                    "reason", reason
                )
            );
            logRoundClosed(sessionId, round.getId(), "PASSED", reason);
        } else if (round.getStatus() == RoundStatus.SOLD) {
            ActivePlayer winner = session.getPlayers().stream()
                .filter(p -> p.getId().equals(round.getHighestBidderId()))
                .findFirst().orElse(null);
            if (winner != null) {
                sseEmitterRegistry.broadcast(
                    sessionId, "round_ended",
                    Map.of(
                        "round", AuctionRoundResponses.toResponse(round, roundCard, session, cardMap),
                        "result", "SOLD",
                        "winnerUserId", winner.getUserId()
                    )
                );
            }
            logRoundClosed(sessionId, round.getId(), "SOLD", null);
        }
    }

    void maybeFinalizeSession(ActiveSession session) {
        if (session.getStatus() != SessionStatus.AUCTIONING) {
            return;
        }
        if (session.playerCount() < 2) {
            return;
        }
        if (!AuctionRules.isSessionRostersFull(session)) {
            return;
        }
        matchTransition.enterMatchStartPending(session, session.getVersion());
    }

    private void broadcastRoundResolving(UUID sessionId, UUID roundId) {
        sseEmitterRegistry.broadcast(
            sessionId, "round_resolving",
            Map.of("sessionId", sessionId, "roundId", roundId)
        );
    }

    private void logRoundClosed(UUID sessionId, UUID roundId, String result, String detail) {
        if (detail == null) {
            log.info("Round closed session={} round={} result={}", sessionId, roundId, result);
        } else {
            log.info("Round closed session={} round={} result={} detail={}", sessionId, roundId, result, detail);
        }
    }
}
