package com.example.auction.competition.control;

import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchRepository;
import com.example.auction.competition.entity.CompetitionMatchStatus;
import com.example.auction.competition.entity.CompetitionMatchViewRepository;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.competition.entity.CompetitionStatus;
import com.example.auction.competition.entity.CompetitionType;
import com.example.auction.simulation.control.DraftCompletedEvent;
import com.example.auction.simulation.control.MatchSimulator;
import com.example.auction.simulation.entity.SimulationResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TournamentBracketService {

    private final CompetitionRepository competitionRepository;
    private final CompetitionMatchRepository matchRepository;
    private final CompetitionMatchViewRepository viewRepository;
    private final MatchSimulator matchSimulator;
    private final CompetitionJson competitionJson;

    public TournamentBracketService(
        CompetitionRepository competitionRepository,
        CompetitionMatchRepository matchRepository,
        CompetitionMatchViewRepository viewRepository,
        MatchSimulator matchSimulator,
        CompetitionJson competitionJson
    ) {
        this.competitionRepository = competitionRepository;
        this.matchRepository = matchRepository;
        this.viewRepository = viewRepository;
        this.matchSimulator = matchSimulator;
        this.competitionJson = competitionJson;
    }

    /** Creates bracket with pending semi-finals only; simulations run when players open each match. */
    @Transactional
    public CompetitionEntity initializeTournament(DraftCompletedEvent event) {
        if (event.players().size() != 4) {
            throw new IllegalStateException("Tournament requires exactly 4 players");
        }

        CompetitionEntity competition = new CompetitionEntity();
        competition.setId(UUID.randomUUID());
        competition.setSessionId(event.sessionId());
        competition.setType(CompetitionType.TOURNAMENT);
        competition.setStatus(CompetitionStatus.IN_PROGRESS);
        competition.setCreatedAt(Instant.now());
        competition.setDraftSnapshotJson(competitionJson.writeSnapshot(TournamentDraftSnapshot.from(event)));
        competitionRepository.save(competition);

        List<UUID> order = event.players().stream().map(DraftCompletedEvent.DraftPlayerRoster::userId).toList();
        matchRepository.save(createMatch(competition.getId(), "SEMI_1", order.get(0), order.get(1), event.seed(), 1));
        matchRepository.save(createMatch(competition.getId(), "SEMI_2", order.get(2), order.get(3), event.seed() + 1, 2));

        return competition;
    }

    @Transactional
    public SimulationResult simulateMatchIfNeeded(CompetitionEntity competition, CompetitionMatchEntity match) {
        if (match.getStatus() == CompetitionMatchStatus.COMPLETED) {
            return new SimulationResult(
                match.getHomeScore() != null ? match.getHomeScore() : 0,
                match.getAwayScore() != null ? match.getAwayScore() : 0,
                competitionJson.readTimeline(match.getReplayDataJson()),
                match.getHomePenaltyScore() != null ? match.getHomePenaltyScore() : 0,
                match.getAwayPenaltyScore() != null ? match.getAwayPenaltyScore() : 0
            );
        }

        assertPlayable(competition, match);

        TournamentDraftSnapshot snapshot = competitionJson.readSnapshot(competition.getDraftSnapshotJson());
        Map<UUID, DraftCompletedEvent.DraftPlayerRoster> byUser = snapshot.players().stream()
            .map(p -> new DraftCompletedEvent.DraftPlayerRoster(
                p.userId(), p.nickname(), p.formation(), p.roster()
            ))
            .collect(Collectors.toMap(DraftCompletedEvent.DraftPlayerRoster::userId, Function.identity()));

        DraftCompletedEvent.DraftPlayerRoster home = byUser.get(match.getHomeUserId());
        DraftCompletedEvent.DraftPlayerRoster away = byUser.get(match.getAwayUserId());

        SimulationResult result = matchSimulator.simulateWithTimeline(
            match.getSeed(),
            home.formation(),
            away.formation(),
            home.roster(),
            away.roster(),
            home.userId(),
            away.userId()
        );

        match.setHomeScore(result.homeGoals());
        match.setAwayScore(result.awayGoals());
        match.setHomePenaltyScore(result.decidedOnPenalties() ? result.homePenaltyGoals() : null);
        match.setAwayPenaltyScore(result.decidedOnPenalties() ? result.awayPenaltyGoals() : null);
        match.setReplayDataJson(competitionJson.writeTimeline(result.timeline()));
        match.setWinnerUserId(result.resolveWinner(match.getHomeUserId(), match.getAwayUserId()));
        match.setStatus(CompetitionMatchStatus.COMPLETED);
        matchRepository.save(match);

        return result;
    }

    public boolean isMatchPlayable(CompetitionEntity competition, CompetitionMatchEntity match) {
        if (match.getStatus() == CompetitionMatchStatus.COMPLETED) {
            return true;
        }
        if (match.getRoundKey().startsWith("SEMI")) {
            return true;
        }
        if ("FINAL".equals(match.getRoundKey())) {
            return semisFullyWatched(competition.getId());
        }
        return false;
    }

    public boolean allPlayersWatched(CompetitionEntity competition, CompetitionMatchEntity match) {
        long views = viewRepository.countByMatchId(match.getId());
        return views >= snapshotPlayerCount(competition);
    }

    @Transactional
    public void ensureFinalMatch(CompetitionEntity competition) {
        if (matchRepository.findByCompetitionIdAndRoundKey(competition.getId(), "FINAL").isPresent()) {
            return;
        }
        if (!semisFullyWatched(competition.getId())) {
            return;
        }

        CompetitionMatchEntity semi1 = matchRepository.findByCompetitionIdAndRoundKey(competition.getId(), "SEMI_1")
            .orElseThrow();
        CompetitionMatchEntity semi2 = matchRepository.findByCompetitionIdAndRoundKey(competition.getId(), "SEMI_2")
            .orElseThrow();

        if (semi1.getWinnerUserId() == null || semi2.getWinnerUserId() == null) {
            return;
        }

        TournamentDraftSnapshot snapshot = competitionJson.readSnapshot(competition.getDraftSnapshotJson());
        CompetitionMatchEntity finalMatch = createMatch(
            competition.getId(),
            "FINAL",
            semi1.getWinnerUserId(),
            semi2.getWinnerUserId(),
            snapshot.seed() + 2,
            3
        );
        matchRepository.save(finalMatch);
    }

    @Transactional
    public CompetitionEntity tryCompleteTournament(CompetitionEntity competition) {
        var finalOpt = matchRepository.findByCompetitionIdAndRoundKey(competition.getId(), "FINAL");
        if (finalOpt.isEmpty()) {
            return competition;
        }
        CompetitionMatchEntity finalMatch = finalOpt.get();
        if (finalMatch.getStatus() != CompetitionMatchStatus.COMPLETED || !allPlayersWatched(competition, finalMatch)) {
            return competition;
        }
        competition.setChampionUserId(finalMatch.getWinnerUserId());
        competition.setStatus(CompetitionStatus.COMPLETED);
        return competitionRepository.save(competition);
    }

    private boolean semisFullyWatched(UUID competitionId) {
        CompetitionMatchEntity semi1 = matchRepository.findByCompetitionIdAndRoundKey(competitionId, "SEMI_1").orElse(null);
        CompetitionMatchEntity semi2 = matchRepository.findByCompetitionIdAndRoundKey(competitionId, "SEMI_2").orElse(null);
        if (semi1 == null || semi2 == null) {
            return false;
        }
        CompetitionEntity competition = competitionRepository.findById(competitionId).orElseThrow();
        return semi1.getStatus() == CompetitionMatchStatus.COMPLETED
            && semi2.getStatus() == CompetitionMatchStatus.COMPLETED
            && allPlayersWatched(competition, semi1)
            && allPlayersWatched(competition, semi2);
    }

    private void assertPlayable(CompetitionEntity competition, CompetitionMatchEntity match) {
        if (!isMatchPlayable(competition, match)) {
            throw new IllegalStateException("Match is locked until all players watch both semi-finals");
        }
    }

    public int snapshotPlayerCount(CompetitionEntity competition) {
        return competitionJson.readSnapshot(competition.getDraftSnapshotJson()).playerCount();
    }

    public TournamentDraftSnapshot readSnapshot(CompetitionEntity competition) {
        return competitionJson.readSnapshot(competition.getDraftSnapshotJson());
    }

    private CompetitionMatchEntity createMatch(
        UUID competitionId, String roundKey, UUID homeUserId, UUID awayUserId, long seed, int sortOrder
    ) {
        CompetitionMatchEntity match = new CompetitionMatchEntity();
        match.setId(UUID.randomUUID());
        match.setCompetitionId(competitionId);
        match.setRoundKey(roundKey);
        match.setHomeUserId(homeUserId);
        match.setAwayUserId(awayUserId);
        match.setStatus(CompetitionMatchStatus.PENDING);
        match.setSeed(seed);
        match.setSortOrder(sortOrder);
        return match;
    }

}
