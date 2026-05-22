package dev.sportdraft.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sportdraft.bidding.boundary.AuctionRoundResponse;
import dev.sportdraft.bidding.boundary.PlaceBidRequest;
import dev.sportdraft.bidding.control.AuctionService;
import dev.sportdraft.lobby.control.LobbyService;
import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActiveRound;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuctionAntiSnipeIT extends AbstractIntegrationTest {

    private static final long ANTI_SNIPE_EXTENSION_SECONDS = 5L;

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Test
    void placeBid_doesNotExtendWhenMoreThanNineSecondsRemain() {
        AuctionContext ctx = startAuction();

        Instant originalEndsAt = Instant.now().plus(14, ChronoUnit.SECONDS);
        setOpenRoundEndsAt(ctx.sessionId(), originalEndsAt);

        AuctionRoundResponse response = placeOpeningBid(ctx);

        assertThat(response.endsAt()).isEqualTo(originalEndsAt);
    }

    @Test
    void placeBid_extendsWhenNineOrFewerSecondsRemain() {
        AuctionContext ctx = startAuction();

        Instant originalEndsAt = Instant.now().plus(9, ChronoUnit.SECONDS);
        setOpenRoundEndsAt(ctx.sessionId(), originalEndsAt);

        AuctionRoundResponse response = placeOpeningBid(ctx);

        assertThat(response.endsAt()).isEqualTo(
            originalEndsAt.plus(ANTI_SNIPE_EXTENSION_SECONDS, ChronoUnit.SECONDS)
        );
    }

    @Test
    void placeBid_doesNotExtendAfterAntiSnipeWhenTwelveSecondsRemain() {
        AuctionContext ctx = startAuction();

        Instant originalEndsAt = Instant.now().plus(12, ChronoUnit.SECONDS);
        setOpenRoundEndsAt(ctx.sessionId(), originalEndsAt);

        AuctionRoundResponse response = placeOpeningBid(ctx);

        assertThat(response.endsAt()).isEqualTo(originalEndsAt);
    }

    private AuctionContext startAuction() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        UUID sessionId = lobbyService.joinLobby(user1, "SnipeHost").sessionId();
        lobbyService.joinLobby(user2, "SnipeGuest");

        ActiveSession session = sessionStore.getById(sessionId);
        long version = session.getVersion();
        session.moveToCountdown(Instant.now().minusSeconds(1));
        sessionStore.saveIfVersionMatch(session, version);
        lobbyService.advanceTimedPhases();

        lobbyService.selectFormation(sessionId, user1, Formation.FOUR_FOUR_TWO);
        lobbyService.selectFormation(sessionId, user2, Formation.FOUR_THREE_THREE);
        lobbyService.confirmFormationReady(sessionId, user1);
        lobbyService.confirmFormationReady(sessionId, user2);

        session = sessionStore.getById(sessionId);
        version = session.getVersion();
        session.setCountdownEndsAt(Instant.now().minusSeconds(1));
        sessionStore.saveIfVersionMatch(session, version);
        lobbyService.advanceTimedPhases();

        assertThat(lobbyService.getSnapshot(sessionId, user1).status()).isEqualTo(SessionStatus.AUCTIONING);
        assertThat(auctionService.getAuctionState(sessionId, user1).currentRound()).isNotNull();

        return new AuctionContext(sessionId, user1, user2);
    }

    private void setOpenRoundEndsAt(UUID sessionId, Instant endsAt) {
        ActiveSession session = sessionStore.getById(sessionId);
        ActiveRound openRound = session.findOpenRound().orElseThrow();
        openRound.setEndsAt(endsAt);
        sessionStore.saveIfVersionMatch(session, session.getVersion());
    }

    private AuctionRoundResponse placeOpeningBid(AuctionContext ctx) {
        AuctionRoundResponse round = auctionService.getAuctionState(ctx.sessionId(), ctx.user1()).currentRound();
        assertThat(round).isNotNull();

        IntegrationAuctionTestSupport.openBiddingOnCurrentRound(sessionStore, ctx.sessionId());

        UUID bidder = round.currentTurnUserId() != null ? round.currentTurnUserId() : ctx.user1();
        return auctionService.placeBid(
            ctx.sessionId(),
            bidder,
            UUID.randomUUID().toString(),
            new PlaceBidRequest(round.roundId(), 1)
        );
    }

    private record AuctionContext(UUID sessionId, UUID user1, UUID user2) {}
}
