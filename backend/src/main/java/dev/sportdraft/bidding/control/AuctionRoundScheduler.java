package dev.sportdraft.bidding.control;

import dev.sportdraft.bidding.entity.Card;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActiveRound;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import dev.sportdraft.session.redis.SessionLock;
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
    private final SessionLock sessionLock;
    private final CardCatalogService cardCatalog;
    private final AuctionSettlementService settlement;
    private final AuctionMatchTransition matchTransition;

    public AuctionRoundScheduler(
        ActiveSessionStore sessionStore,
        SessionLock sessionLock,
        CardCatalogService cardCatalog,
        AuctionSettlementService settlement,
        AuctionMatchTransition matchTransition
    ) {
        this.sessionStore = sessionStore;
        this.sessionLock = sessionLock;
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
                sessionLock.runVoid(sessionId, () -> {
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
            sessionLock.runVoid(sessionId, () -> {
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
