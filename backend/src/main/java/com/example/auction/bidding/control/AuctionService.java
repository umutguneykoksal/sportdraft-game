package com.example.auction.bidding.control;

import com.example.auction.bidding.boundary.AuctionRoundResponse;
import com.example.auction.bidding.boundary.AuctionStateResponse;
import com.example.auction.bidding.boundary.AuctionTeamCardResponse;
import com.example.auction.bidding.boundary.PlayerAuctionView;
import com.example.auction.bidding.boundary.PlaceBidRequest;
import com.example.auction.bidding.entity.Card;
import com.example.auction.bidding.entity.RoundStatus;
import com.example.auction.session.control.SessionAccess;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveRound;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.session.redis.SessionMutationGuard;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Thin facade for auction commands and read models. Round lifecycle, settlement,
 * match transition, and scheduled ticks live in dedicated control services.
 */
@Service
public class AuctionService {

    private final ActiveSessionStore sessionStore;
    private final SessionMutationGuard mutationGuard;
    private final CardCatalogService cardCatalog;
    private final AuctionRoundLifecycle roundLifecycle;
    private final AuctionMatchTransition matchTransition;

    public AuctionService(
        ActiveSessionStore sessionStore,
        SessionMutationGuard mutationGuard,
        CardCatalogService cardCatalog,
        AuctionRoundLifecycle roundLifecycle,
        AuctionMatchTransition matchTransition
    ) {
        this.sessionStore = sessionStore;
        this.mutationGuard = mutationGuard;
        this.cardCatalog = cardCatalog;
        this.roundLifecycle = roundLifecycle;
        this.matchTransition = matchTransition;
    }

    public void confirmMatchStart(UUID sessionId, UUID userId) {
        mutationGuard.mutateVoid(sessionId, () -> {
            ActiveSession session = sessionStore.getById(sessionId);
            matchTransition.confirmMatchStart(session, userId);
        });
    }

    public AuctionRoundResponse startRound(UUID sessionId, UUID userId) {
        return mutationGuard.mutate(sessionId, () -> {
            ActiveSession session = sessionStore.getById(sessionId);
            SessionAccess.requireMember(session, userId);
            return roundLifecycle.startRound(session);
        });
    }

    public AuctionRoundResponse placeBid(UUID sessionId, UUID userId, String idempotencyKey, PlaceBidRequest request) {
        return mutationGuard.mutate(sessionId, () -> roundLifecycle.placeBid(sessionId, userId, idempotencyKey, request));
    }

    public AuctionRoundResponse readyNext(UUID sessionId, UUID userId, UUID roundId) {
        return mutationGuard.mutate(sessionId, () -> roundLifecycle.readyNext(sessionId, userId, roundId));
    }

    public AuctionRoundResponse passRound(UUID sessionId, UUID userId, UUID roundId) {
        return mutationGuard.mutate(sessionId, () -> roundLifecycle.passRound(sessionId, userId, roundId));
    }

    public AuctionStateResponse getAuctionState(UUID sessionId, UUID userId) {
        ActiveSession session = sessionStore.getById(sessionId);
        Map<UUID, Card> cardMap = cardCatalog.getCardMap();
        TeamSnapshot teams = buildTeamSnapshot(session, userId);

        AuctionRoundResponse currentRound = session.findOpenRound()
            .map(r -> AuctionRoundResponses.toResponseWithContext(r, cardMap.get(r.getCardId()), session, cardMap, userId))
            .orElse(null);

        List<PlayerAuctionView> players = session.getMaxPlayers() > 2
            ? buildPlayerViews(session, cardMap)
            : List.of();

        return new AuctionStateResponse(
            currentRound,
            teams.myTeam(),
            teams.opponentTeam(),
            teams.myFormation(),
            teams.opponentFormation(),
            session.getVersion(),
            players
        );
    }

    private List<PlayerAuctionView> buildPlayerViews(ActiveSession session, Map<UUID, Card> cardMap) {
        List<ActiveRound> soldRounds = session.roundsByStatus(RoundStatus.SOLD);
        Map<UUID, Long> soldAmountByCardId = new HashMap<>();
        Map<UUID, Integer> draftOrderByCardId = new HashMap<>();
        int draftIndex = 0;
        for (ActiveRound sr : soldRounds) {
            soldAmountByCardId.put(sr.getCardId(), sr.getHighestBidCents());
            draftOrderByCardId.put(sr.getCardId(), draftIndex++);
        }

        UUID turnUserId = session.findOpenRound().map(ActiveRound::getCurrentTurnUserId).orElse(null);
        session.ensureTurnOrder();
        return session.getTurnOrder().stream()
            .map(uid -> session.findPlayerByUserId(uid).orElse(null))
            .filter(p -> p != null)
            .map(p -> new PlayerAuctionView(
                p.getUserId(),
                p.getNickname(),
                p.getRemainingBudgetCents(),
                p.getCardIds().size(),
                p.getUserId().equals(turnUserId),
                p.getFormation().name(),
                AuctionRoundResponses.buildTeamCards(p, cardMap, soldAmountByCardId, draftOrderByCardId)
            ))
            .toList();
    }

    public TeamSnapshot buildTeamSnapshot(ActiveSession session, UUID userId) {
        Map<UUID, Card> cardMap = cardCatalog.getCardMap();

        if (session.playerCount() < 2) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session does not have two players");
        }
        SessionAccess.requireMember(session, userId);
        ActivePlayer me = session.findPlayerByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Player not found in session"));
        ActivePlayer opponent = session.getPlayers().stream()
            .filter(p -> !p.getUserId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Opponent not found in session"));

        List<ActiveRound> soldRounds = session.roundsByStatus(RoundStatus.SOLD);
        Map<UUID, Long> soldAmountByCardId = new HashMap<>();
        Map<UUID, Integer> draftOrderByCardId = new HashMap<>();
        int draftIndex = 0;
        for (ActiveRound sr : soldRounds) {
            soldAmountByCardId.put(sr.getCardId(), sr.getHighestBidCents());
            draftOrderByCardId.put(sr.getCardId(), draftIndex++);
        }

        return new TeamSnapshot(
            AuctionRoundResponses.buildTeamCards(me, cardMap, soldAmountByCardId, draftOrderByCardId),
            AuctionRoundResponses.buildTeamCards(opponent, cardMap, soldAmountByCardId, draftOrderByCardId),
            me.getFormation().name(),
            opponent.getFormation().name()
        );
    }

    public record TeamSnapshot(
        List<AuctionTeamCardResponse> myTeam,
        List<AuctionTeamCardResponse> opponentTeam,
        String myFormation,
        String opponentFormation
    ) {}

    public SpectateSnapshot buildSpectateSnapshot(ActiveSession session) {
        Map<UUID, Card> cardMap = cardCatalog.getCardMap();
        List<ActivePlayer> sorted = session.playersSortedById();
        if (sorted.size() < 2) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session does not have two players");
        }
        ActivePlayer home = sorted.get(0);
        ActivePlayer away = sorted.get(1);

        List<ActiveRound> soldRounds = session.roundsByStatus(RoundStatus.SOLD);
        Map<UUID, Long> soldAmountByCardId = new HashMap<>();
        Map<UUID, Integer> draftOrderByCardId = new HashMap<>();
        int draftIndex = 0;
        for (ActiveRound sr : soldRounds) {
            soldAmountByCardId.put(sr.getCardId(), sr.getHighestBidCents());
            draftOrderByCardId.put(sr.getCardId(), draftIndex++);
        }

        AuctionRoundResponse currentRound = session.findOpenRound()
            .map(r -> AuctionRoundResponses.toResponse(r, cardMap.get(r.getCardId()), session, cardMap))
            .orElse(null);

        return new SpectateSnapshot(
            session.getStatus().name(),
            currentRound,
            AuctionRoundResponses.buildTeamCards(home, cardMap, soldAmountByCardId, draftOrderByCardId),
            AuctionRoundResponses.buildTeamCards(away, cardMap, soldAmountByCardId, draftOrderByCardId),
            home.getFormation().name(),
            away.getFormation().name(),
            home.getUserId(),
            away.getUserId(),
            home.getNickname(),
            away.getNickname(),
            home.getRemainingBudgetCents(),
            away.getRemainingBudgetCents(),
            session.getHomeScore() != null ? session.getHomeScore() : 0,
            session.getAwayScore() != null ? session.getAwayScore() : 0
        );
    }

    public record SpectateSnapshot(
        String status,
        AuctionRoundResponse currentRound,
        List<AuctionTeamCardResponse> homeTeam,
        List<AuctionTeamCardResponse> awayTeam,
        String homeFormation,
        String awayFormation,
        UUID homeUserId,
        UUID awayUserId,
        String homeNickname,
        String awayNickname,
        long homeBudget,
        long awayBudget,
        int homeScore,
        int awayScore
    ) {}

    public void ensureOpenRound(UUID sessionId) {
        mutationGuard.mutateVoid(sessionId, () -> {
            ActiveSession session = sessionStore.getById(sessionId);
            roundLifecycle.ensureOpenRound(session);
        });
    }
}
