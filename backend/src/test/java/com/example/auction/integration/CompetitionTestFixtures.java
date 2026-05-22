package com.example.auction.integration;

import com.example.auction.bidding.control.CardCatalogService;
import com.example.auction.bidding.entity.PlayerAttributes;
import com.example.auction.bidding.entity.Position;
import com.example.auction.lobby.control.LobbyService;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.simulation.control.DraftCompletedEvent;
import com.example.auction.simulation.control.SessionFinalizedEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class CompetitionTestFixtures {

    static final UUID USER_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID USER_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID USER_3 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID USER_4 = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private CompetitionTestFixtures() {}

    static DraftCompletedEvent tournamentEvent(UUID sessionId) {
        return draftEvent(sessionId, GameMode.TOURNAMENT, 424242L);
    }

    static DraftCompletedEvent tournamentEventWithId(UUID sessionId, UUID eventId) {
        return draftEventWithId(eventId, sessionId, GameMode.TOURNAMENT, 424242L);
    }

    static DraftCompletedEvent leagueEvent(UUID sessionId) {
        return draftEvent(sessionId, GameMode.LEAGUE, 525252L);
    }

    static DraftCompletedEvent draftEvent(UUID sessionId, GameMode gameMode, long seed) {
        return draftEventWithId(UUID.randomUUID(), sessionId, gameMode, seed);
    }

    static DraftCompletedEvent draftEventWithId(UUID eventId, UUID sessionId, GameMode gameMode, long seed) {
        return new DraftCompletedEvent(
            eventId,
            Instant.now(),
            sessionId,
            gameMode,
            seed,
            List.of(
                playerRoster(USER_1, "Player-1", Formation.FOUR_FOUR_TWO, 82),
                playerRoster(USER_2, "Player-2", Formation.FOUR_THREE_THREE, 80),
                playerRoster(USER_3, "Player-3", Formation.FOUR_FOUR_TWO, 79),
                playerRoster(USER_4, "Player-4", Formation.FOUR_THREE_THREE, 78)
            )
        );
    }

    static void advanceToAuctioning(
        LobbyService lobbyService,
        ActiveSessionStore sessionStore,
        UUID sessionId,
        UUID... userIds
    ) {
        ActiveSession session = sessionStore.getById(sessionId);
        long version = session.getVersion();
        session.moveToCountdown(Instant.now().minusSeconds(1));
        sessionStore.saveIfVersionMatch(session, version);
        lobbyService.advanceTimedPhases();

        for (UUID userId : userIds) {
            lobbyService.selectFormation(sessionId, userId, Formation.FOUR_FOUR_TWO);
        }
        for (UUID userId : userIds) {
            lobbyService.confirmFormationReady(sessionId, userId);
        }

        session = sessionStore.getById(sessionId);
        version = session.getVersion();
        session.setCountdownEndsAt(Instant.now().minusSeconds(1));
        sessionStore.saveIfVersionMatch(session, version);
        lobbyService.advanceTimedPhases();
    }

    static void fillFullRosters(
        ActiveSession session,
        CardCatalogService catalog,
        ActiveSessionStore sessionStore
    ) {
        List<UUID> pool = catalog.getAllCardIds();
        int required = session.getPlayers().size() * 11;
        if (pool.size() < required) {
            throw new IllegalStateException("Not enough cards in catalog for full rosters: need " + required);
        }

        int index = 0;
        long version = session.getVersion();
        for (ActivePlayer player : session.getPlayers()) {
            List<UUID> cardIds = new ArrayList<>();
            for (int slot = 0; slot < 11; slot++) {
                cardIds.add(pool.get(index++));
            }
            player.setCardIds(cardIds);
        }
        sessionStore.saveIfVersionMatch(session, version);
    }

    static ActiveSession activeSession(UUID sessionId, GameMode gameMode) {
        ActiveSession session = new ActiveSession(sessionId, SessionStatus.FINALIZING, Instant.now(), gameMode);
        session.setMatchSeed(424242L);
        session.setTurnOrder(List.of(USER_1, USER_2, USER_3, USER_4));
        List<ActivePlayer> players = new ArrayList<>();
        players.add(activePlayer(USER_1, "Player-1", Formation.FOUR_FOUR_TWO));
        players.add(activePlayer(USER_2, "Player-2", Formation.FOUR_THREE_THREE));
        players.add(activePlayer(USER_3, "Player-3", Formation.FOUR_FOUR_TWO));
        players.add(activePlayer(USER_4, "Player-4", Formation.FOUR_THREE_THREE));
        session.setPlayers(players);
        return session;
    }

    private static ActivePlayer activePlayer(UUID userId, String nickname, Formation formation) {
        ActivePlayer player = new ActivePlayer(UUID.randomUUID(), userId, nickname, formation, 0);
        player.setFormationConfirmed(true);
        List<UUID> cardIds = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            cardIds.add(UUID.nameUUIDFromBytes((userId.toString() + "-card-" + i).getBytes()));
        }
        player.setCardIds(cardIds);
        return player;
    }

    private static DraftCompletedEvent.DraftPlayerRoster playerRoster(
        UUID userId,
        String nickname,
        Formation formation,
        int baseRating
    ) {
        List<SessionFinalizedEvent.RatedCard> roster = new ArrayList<>();
        Position[] slots = {
            Position.GK, Position.DEF, Position.DEF, Position.DEF, Position.DEF,
            Position.CDM, Position.CM, Position.CM, Position.CAM,
            Position.FWD, Position.FWD
        };
        for (int i = 0; i < slots.length; i++) {
            roster.add(new SessionFinalizedEvent.RatedCard(
                UUID.nameUUIDFromBytes((userId.toString() + "-rated-" + i).getBytes()),
                slots[i],
                baseRating - (i % 3),
                nickname + " " + slots[i],
                new PlayerAttributes(baseRating, baseRating, baseRating, baseRating, baseRating)
            ));
        }
        return new DraftCompletedEvent.DraftPlayerRoster(userId, nickname, formation, roster);
    }
}
