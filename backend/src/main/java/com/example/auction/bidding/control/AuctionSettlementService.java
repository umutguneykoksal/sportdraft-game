package com.example.auction.bidding.control;

import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.RoundStatus;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveRound;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.common.sse.SseEmitterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuctionSettlementService {

    private final ActiveSessionStore sessionStore;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final MeterRegistry meterRegistry;
    private final AuctionMatchTransition matchTransition;
    private final AuctionRoundLifecycle roundLifecycle;

    public AuctionSettlementService(
        ActiveSessionStore sessionStore,
        SseEmitterRegistry sseEmitterRegistry,
        MeterRegistry meterRegistry,
        AuctionMatchTransition matchTransition,
        @Lazy AuctionRoundLifecycle roundLifecycle
    ) {
        this.sessionStore = sessionStore;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.meterRegistry = meterRegistry;
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
            meterRegistry.counter("auction.round.closed", "result", "PASSED").increment();
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
            meterRegistry.counter("auction.round.closed", "result", "PASSED").increment();
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
            meterRegistry.counter("auction.round.closed", "result", "PASSED").increment();
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
        meterRegistry.counter("auction.round.closed", "result", "SOLD").increment();
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
        if (round.getStatus() == RoundStatus.PASSED) {
            meterRegistry.counter("auction.round.closed", "result", "PASSED").increment();
        }
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
            meterRegistry.counter("auction.round.closed", "result", "SOLD").increment();
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
}
