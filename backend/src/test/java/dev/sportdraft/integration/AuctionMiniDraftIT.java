package dev.sportdraft.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sportdraft.bidding.boundary.AuctionRoundResponse;
import dev.sportdraft.bidding.boundary.PlaceBidRequest;
import dev.sportdraft.bidding.control.AuctionRoundScheduler;
import dev.sportdraft.bidding.control.AuctionService;
import dev.sportdraft.lobby.boundary.SessionSnapshotResponse;
import dev.sportdraft.lobby.control.LobbyService;
import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActiveRound;
import dev.sportdraft.session.redis.ActiveSession;
import dev.sportdraft.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuctionMiniDraftIT extends AbstractIntegrationTest {

    private static final int MINI_DRAFT_ROUNDS = 5;

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Autowired
    private AuctionRoundScheduler auctionRoundScheduler;

    @Test
    void miniDraft_settlesMultipleRoundsWithoutRosterShortcut() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        SessionSnapshotResponse joined = lobbyService.joinLobby(user1, "DraftHost");
        lobbyService.joinLobby(user2, "DraftGuest");
        UUID sessionId = joined.sessionId();

        advanceToAuctioning(sessionId, user1, user2);

        int cardsBefore = totalRosterCards(sessionId);
        assertThat(cardsBefore).isZero();

        for (int round = 0; round < MINI_DRAFT_ROUNDS; round++) {
            AuctionRoundResponse current = auctionService.getAuctionState(sessionId, user1).currentRound();
            assertThat(current).isNotNull();
            assertThat(current.status()).isEqualTo("OPEN");

            UUID bidder = current.currentTurnUserId() != null ? current.currentTurnUserId() : user1;
            IntegrationAuctionTestSupport.openBiddingOnCurrentRound(sessionStore, sessionId);
            auctionService.placeBid(
                sessionId,
                bidder,
                UUID.randomUUID().toString(),
                new PlaceBidRequest(current.roundId(), 1)
            );

            expireOpenRound(sessionId);
        }

        int cardsAfter = totalRosterCards(sessionId);
        assertThat(cardsAfter).isEqualTo(MINI_DRAFT_ROUNDS);
        assertThat(sessionStore.getById(sessionId).getStatus()).isEqualTo(SessionStatus.AUCTIONING);
    }

    private void advanceToAuctioning(UUID sessionId, UUID user1, UUID user2) {
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
    }

    private void expireOpenRound(UUID sessionId) {
        ActiveSession session = sessionStore.getById(sessionId);
        ActiveRound openRound = session.findOpenRound().orElseThrow();
        openRound.setEndsAt(Instant.now().minusSeconds(30));
        long version = session.getVersion();
        sessionStore.saveIfVersionMatch(session, version);
        auctionRoundScheduler.processAuctioningSessions();
    }

    private int totalRosterCards(UUID sessionId) {
        ActiveSession session = sessionStore.getById(sessionId);
        return session.getPlayers().stream()
            .mapToInt(player -> player.getCardIds().size())
            .sum();
    }
}
