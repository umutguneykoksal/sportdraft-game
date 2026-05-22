package com.example.auction.integration;

import com.example.auction.bidding.control.AuctionService;
import com.example.auction.bidding.control.CardCatalogService;
import com.example.auction.bidding.entity.PlayerAttributes;
import com.example.auction.bidding.entity.Position;
import com.example.auction.competition.control.CompetitionMatchService;
import com.example.auction.competition.control.LeagueFixtureService;
import com.example.auction.competition.control.TournamentBracketService;
import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchRepository;
import com.example.auction.competition.entity.CompetitionMatchStatus;
import com.example.auction.competition.entity.CompetitionRepository;
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

    static void watchMatch(
        CompetitionMatchService service,
        UUID sessionId,
        CompetitionMatchEntity match
    ) {
        service.openMatch(sessionId, match.getId(), match.getHomeUserId(), false);
        service.acknowledgeWatch(sessionId, match.getId(), match.getHomeUserId());
        service.acknowledgeWatch(sessionId, match.getId(), match.getAwayUserId());
    }

    static void watchMatchWithAllParticipants(
        CompetitionMatchService service,
        UUID sessionId,
        CompetitionMatchEntity match,
        UUID... allUsers
    ) {
        service.openMatch(sessionId, match.getId(), match.getHomeUserId(), false);
        for (UUID userId : allUsers) {
            service.acknowledgeWatch(sessionId, match.getId(), userId);
        }
    }

    static CompetitionEntity advanceTournamentIntermission(
        TournamentBracketService bracketService,
        CompetitionRepository competitionRepository,
        CompetitionEntity competition
    ) {
        CompetitionEntity fresh = competitionRepository.findById(competition.getId()).orElseThrow();
        fresh.setIntermissionEndsAt(Instant.now().minusSeconds(1));
        competitionRepository.save(fresh);
        bracketService.tryStartFinalPhase(fresh);
        return competitionRepository.findById(fresh.getId()).orElseThrow();
    }

    static CompetitionEntity advanceLeagueBreak(
        LeagueFixtureService fixtureService,
        CompetitionRepository competitionRepository,
        CompetitionEntity competition
    ) {
        CompetitionEntity fresh = competitionRepository.findById(competition.getId()).orElseThrow();
        fresh.setIntermissionEndsAt(Instant.now().minusSeconds(1));
        competitionRepository.save(fresh);
        fixtureService.tryAdvanceFromBreak(fresh);
        return competitionRepository.findById(fresh.getId()).orElseThrow();
    }

    static void completeDuelDraft(
        AuctionService auctionService,
        CardCatalogService catalog,
        ActiveSessionStore sessionStore,
        UUID sessionId,
        UUID user1,
        UUID user2
    ) {
        ActiveSession session = sessionStore.getById(sessionId);
        fillFullRosters(session, catalog, sessionStore);
        auctionService.ensureOpenRound(sessionId);
        auctionService.confirmMatchStart(sessionId, user1);
        auctionService.confirmMatchStart(sessionId, user2);
    }

    static void completeCompetitionDraft(
        AuctionService auctionService,
        CardCatalogService catalog,
        ActiveSessionStore sessionStore,
        UUID sessionId,
        UUID... userIds
    ) {
        ActiveSession session = sessionStore.getById(sessionId);
        fillFullRosters(session, catalog, sessionStore);
        auctionService.ensureOpenRound(sessionId);
        for (UUID userId : userIds) {
            auctionService.confirmMatchStart(sessionId, userId);
        }
    }

    static CompetitionMatchEntity requireMatch(
        CompetitionMatchRepository matchRepository,
        UUID competitionId,
        String roundKey
    ) {
        return matchRepository.findByCompetitionIdAndRoundKey(competitionId, roundKey).orElseThrow();
    }

    static void watchMatchday(
        CompetitionMatchService service,
        CompetitionMatchRepository matchRepository,
        UUID sessionId,
        UUID competitionId,
        int matchday
    ) {
        watchMatch(service, sessionId, requireMatch(matchRepository, competitionId, "MD" + matchday + "_A"));
        watchMatch(service, sessionId, requireMatch(matchRepository, competitionId, "MD" + matchday + "_B"));
    }

    static void forceResult(
        CompetitionMatchRepository matchRepository,
        CompetitionMatchEntity match,
        int homeScore,
        int awayScore
    ) {
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        match.setStatus(CompetitionMatchStatus.COMPLETED);
        if (homeScore > awayScore) {
            match.setWinnerUserId(match.getHomeUserId());
        } else if (awayScore > homeScore) {
            match.setWinnerUserId(match.getAwayUserId());
        } else {
            match.setWinnerUserId(null);
        }
        matchRepository.save(match);
    }

    static void forceDraw(CompetitionMatchRepository matchRepository, CompetitionMatchEntity match) {
        forceResult(matchRepository, match, 1, 1);
    }

    static void openSimulateAndForceResult(
        CompetitionMatchService service,
        CompetitionMatchRepository matchRepository,
        UUID sessionId,
        CompetitionMatchEntity match,
        int homeScore,
        int awayScore
    ) {
        service.openMatch(sessionId, match.getId(), match.getHomeUserId(), false);
        CompetitionMatchEntity updated = matchRepository.findById(match.getId()).orElseThrow();
        forceResult(matchRepository, updated, homeScore, awayScore);
    }

    static void watchMatchWithForcedResult(
        CompetitionMatchService service,
        CompetitionMatchRepository matchRepository,
        UUID sessionId,
        CompetitionMatchEntity match,
        int homeScore,
        int awayScore
    ) {
        service.openMatch(sessionId, match.getId(), match.getHomeUserId(), false);
        forceResult(matchRepository, matchRepository.findById(match.getId()).orElseThrow(), homeScore, awayScore);
        service.acknowledgeWatch(sessionId, match.getId(), match.getHomeUserId());
        service.acknowledgeWatch(sessionId, match.getId(), match.getAwayUserId());
    }

    static void watchMatchdayWithResults(
        CompetitionMatchService service,
        CompetitionMatchRepository matchRepository,
        UUID sessionId,
        UUID competitionId,
        int matchday,
        int[][] scores
    ) {
        watchMatchWithForcedResult(
            service, matchRepository, sessionId,
            requireMatch(matchRepository, competitionId, "MD" + matchday + "_A"),
            scores[0][0], scores[0][1]
        );
        watchMatchWithForcedResult(
            service, matchRepository, sessionId,
            requireMatch(matchRepository, competitionId, "MD" + matchday + "_B"),
            scores[1][0], scores[1][1]
        );
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
