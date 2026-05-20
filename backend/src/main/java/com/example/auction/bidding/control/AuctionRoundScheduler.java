package com.example.auction.bidding.control;

import com.example.auction.bidding.entity.Card;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActiveRound;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.session.redis.SessionMutationGuard;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Timer-driven auction maintenance (grace close, match-start countdown).
 * Round bid/pass/turn logic runs on REST handlers only.
 */
@Component
public class AuctionRoundScheduler {

    private final ActiveSessionStore sessionStore;
    private final SessionMutationGuard mutationGuard;
    private final CardCatalogService cardCatalog;
    private final AuctionSettlementService settlement;
    private final AuctionMatchTransition matchTransition;

    public AuctionRoundScheduler(
        ActiveSessionStore sessionStore,
        SessionMutationGuard mutationGuard,
        CardCatalogService cardCatalog,
        AuctionSettlementService settlement,
        AuctionMatchTransition matchTransition
    ) {
        this.sessionStore = sessionStore;
        this.mutationGuard = mutationGuard;
        this.cardCatalog = cardCatalog;
        this.settlement = settlement;
        this.matchTransition = matchTransition;
    }

    @Scheduled(fixedDelay = 1000)
    public void processAuctioningSessions() {
        Instant now = Instant.now();
        Map<UUID, Card> cardMap = cardCatalog.getCardMap();
        for (ActiveSession snapshot : sessionStore.findByStatus(SessionStatus.AUCTIONING)) {
            UUID sessionId = snapshot.getId();
            try {
                mutationGuard.mutateVoid(sessionId, () -> {
                    ActiveSession session = sessionStore.getById(sessionId);
                    if (session.getStatus() != SessionStatus.AUCTIONING) {
                        return;
                    }
                    Optional<ActiveRound> openOpt = session.findOpenRound();
                    if (openOpt.isPresent()) {
                        ActiveRound round = openOpt.get();
                        Instant graceDeadline = round.getEndsAt().plusMillis(AuctionTiming.BID_GRACE_PERIOD_MS);
                        if (!graceDeadline.isAfter(now)) {
                            settlement.closeRound(round, session, session.getVersion(), cardMap);
                        }
                    }
                    if (AuctionRules.hasFullLobby(session) && AuctionRules.isSessionRostersFull(session)) {
                        matchTransition.enterMatchStartPending(session, session.getVersion());
                    }
                });
            } catch (ActiveSessionStore.OptimisticLockException e) {
                // Retry next tick
            } catch (ResponseStatusException e) {
                if (e.getStatusCode() == HttpStatus.CONFLICT && "Session busy, retry".equals(e.getReason())) {
                    // Lock held by HTTP mutation
                } else {
                    throw e;
                }
            }
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void expireMatchStartCountdowns() {
        Instant now = Instant.now();
        for (ActiveSession snapshot : sessionStore.findByStatus(SessionStatus.MATCH_START_PENDING)) {
            UUID sessionId = snapshot.getId();
            if (snapshot.getMatchStartDeadlineAt() == null || snapshot.getMatchStartDeadlineAt().isAfter(now)) {
                continue;
            }
            mutationGuard.mutateVoid(sessionId, () -> {
                ActiveSession session = sessionStore.getById(sessionId);
                if (session.getStatus() != SessionStatus.MATCH_START_PENDING) {
                    return;
                }
                if (session.getMatchStartDeadlineAt() == null || session.getMatchStartDeadlineAt().isAfter(now)) {
                    return;
                }
                matchTransition.finalizeSessionFromFullRosters(session, session.getVersion());
            });
        }
    }
}
