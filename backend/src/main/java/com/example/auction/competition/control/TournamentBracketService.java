package com.example.auction.competition.control;

import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchRepository;
import com.example.auction.competition.entity.CompetitionMatchStatus;
import com.example.auction.competition.entity.CompetitionMatchViewRepository;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.competition.entity.CompetitionStatus;
import com.example.auction.competition.entity.CompetitionType;
import com.example.auction.competition.entity.TournamentPhase;
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

    public static final long INTERMISSION_SECONDS = 15;

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
        competition.setTournamentPhase(TournamentPhase.SEMIS);
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

        ensureFinalMatch(competition);

        return result;
    }

    public boolean isMandatoryWatchPending(
        CompetitionEntity competition,
        CompetitionMatchEntity match,
        UUID viewerUserId,
        boolean watchedByMe
    ) {
        if (viewerUserId == null || watchedByMe) {
            return false;
        }
        return switch (competition.getTournamentPhase()) {
            case SEMIS -> match.getRoundKey().startsWith("SEMI") && isMatchParticipant(match, viewerUserId);
            case INTERMISSION -> false;
            case FINAL -> "FINAL".equals(match.getRoundKey()) && isTournamentParticipant(competition, viewerUserId);
            case REPLAYS -> false;
        };
    }

    public boolean isReplayAllowed(CompetitionEntity competition, CompetitionMatchEntity match) {
        return competition.getTournamentPhase() == TournamentPhase.REPLAYS
            && match.getStatus() == CompetitionMatchStatus.COMPLETED;
    }

    public boolean scoresVisibleForViewer(
        CompetitionEntity competition,
        CompetitionMatchEntity match,
        boolean watchedByMe
    ) {
        TournamentPhase phase = competition.getTournamentPhase();
        if (match.getStatus() != CompetitionMatchStatus.COMPLETED) {
            return false;
        }
        if (phase == TournamentPhase.REPLAYS) {
            return watchedByMe;
        }
        if (match.getRoundKey().startsWith("SEMI")
            && (phase == TournamentPhase.INTERMISSION || phase == TournamentPhase.FINAL)) {
            return true;
        }
        if ("FINAL".equals(match.getRoundKey()) && phase == TournamentPhase.FINAL) {
            return watchedByMe;
        }
        return watchedByMe;
    }

    public boolean allSemisWatchedByParticipants(UUID competitionId) {
        CompetitionMatchEntity semi1 = matchRepository.findByCompetitionIdAndRoundKey(competitionId, "SEMI_1").orElse(null);
        CompetitionMatchEntity semi2 = matchRepository.findByCompetitionIdAndRoundKey(competitionId, "SEMI_2").orElse(null);
        if (semi1 == null || semi2 == null) {
            return false;
        }
        return semiParticipantsWatched(semi1) && semiParticipantsWatched(semi2);
    }

    @Transactional
    public boolean tryStartIntermissionIfReady(CompetitionEntity competition) {
        if (competition.getTournamentPhase() != TournamentPhase.SEMIS) {
            return false;
        }
        if (!bothSemisSimulated(competition.getId()) || !allSemisWatchedByParticipants(competition.getId())) {
            return false;
        }
        ensureFinalMatch(competition);
        competition.setTournamentPhase(TournamentPhase.INTERMISSION);
        competition.setIntermissionEndsAt(Instant.now().plusSeconds(INTERMISSION_SECONDS));
        competitionRepository.save(competition);
        return true;
    }

    @Transactional
    public boolean tryStartFinalPhase(CompetitionEntity competition) {
        if (competition.getTournamentPhase() != TournamentPhase.INTERMISSION) {
            return false;
        }
        Instant endsAt = competition.getIntermissionEndsAt();
        if (endsAt == null || Instant.now().isBefore(endsAt)) {
            return false;
        }
        competition.setTournamentPhase(TournamentPhase.FINAL);
        competition.setIntermissionEndsAt(null);
        competitionRepository.save(competition);
        return true;
    }

    public boolean isMatchPlayable(CompetitionEntity competition, CompetitionMatchEntity match) {
        return isMatchPlayable(competition, match, null);
    }

    public boolean isMatchPlayable(CompetitionEntity competition, CompetitionMatchEntity match, UUID viewerUserId) {
        if (viewerUserId == null) {
            return match.getStatus() == CompetitionMatchStatus.COMPLETED;
        }
        boolean watched = viewRepository.existsByMatchIdAndUserId(match.getId(), viewerUserId);
        return isMandatoryWatchPending(competition, match, viewerUserId, watched);
    }

    private boolean isTournamentParticipant(CompetitionEntity competition, UUID userId) {
        return readSnapshot(competition).players().stream().anyMatch(p -> p.userId().equals(userId));
    }

    public boolean isMatchParticipant(CompetitionMatchEntity match, UUID userId) {
        return userId != null && (userId.equals(match.getHomeUserId()) || userId.equals(match.getAwayUserId()));
    }

    public boolean semiParticipantsWatched(CompetitionMatchEntity match) {
        return viewRepository.existsByMatchIdAndUserId(match.getId(), match.getHomeUserId())
            && viewRepository.existsByMatchIdAndUserId(match.getId(), match.getAwayUserId());
    }

    public boolean semisReadyForFinal(UUID competitionId) {
        return bothSemisSimulated(competitionId);
    }

    public boolean bothSemisSimulated(UUID competitionId) {
        CompetitionMatchEntity semi1 = matchRepository.findByCompetitionIdAndRoundKey(competitionId, "SEMI_1").orElse(null);
        CompetitionMatchEntity semi2 = matchRepository.findByCompetitionIdAndRoundKey(competitionId, "SEMI_2").orElse(null);
        if (semi1 == null || semi2 == null) {
            return false;
        }
        return semi1.getStatus() == CompetitionMatchStatus.COMPLETED
            && semi2.getStatus() == CompetitionMatchStatus.COMPLETED
            && semi1.getWinnerUserId() != null
            && semi2.getWinnerUserId() != null;
    }

    public int requiredWatchers(CompetitionEntity competition, CompetitionMatchEntity match) {
        if (match.getRoundKey().startsWith("SEMI")) {
            return 2;
        }
        return snapshotPlayerCount(competition);
    }

    public boolean matchFullyWatched(CompetitionEntity competition, CompetitionMatchEntity match) {
        long views = viewRepository.countByMatchId(match.getId());
        return views >= requiredWatchers(competition, match);
    }

    /** @deprecated use {@link #matchFullyWatched(CompetitionEntity, CompetitionMatchEntity)} */
    public boolean allPlayersWatched(CompetitionEntity competition, CompetitionMatchEntity match) {
        return matchFullyWatched(competition, match);
    }

    @Transactional
    public void ensureFinalMatch(CompetitionEntity competition) {
        if (matchRepository.findByCompetitionIdAndRoundKey(competition.getId(), "FINAL").isPresent()) {
            return;
        }

        CompetitionMatchEntity semi1 = matchRepository.findByCompetitionIdAndRoundKey(competition.getId(), "SEMI_1")
            .orElse(null);
        CompetitionMatchEntity semi2 = matchRepository.findByCompetitionIdAndRoundKey(competition.getId(), "SEMI_2")
            .orElse(null);
        if (semi1 == null || semi2 == null) {
            return;
        }
        if (semi1.getStatus() != CompetitionMatchStatus.COMPLETED || semi2.getStatus() != CompetitionMatchStatus.COMPLETED) {
            return;
        }
        if (semi1.getWinnerUserId() == null || semi2.getWinnerUserId() == null) {
            return;
        }

        TournamentDraftSnapshot snapshot = competitionJson.readSnapshot(competition.getDraftSnapshotJson());
        matchRepository.save(createMatch(
            competition.getId(),
            "FINAL",
            semi1.getWinnerUserId(),
            semi2.getWinnerUserId(),
            snapshot.seed() + 2,
            3
        ));
    }

    @Transactional
    public CompetitionEntity tryCompleteTournament(CompetitionEntity competition) {
        var finalOpt = matchRepository.findByCompetitionIdAndRoundKey(competition.getId(), "FINAL");
        if (finalOpt.isEmpty()) {
            return competition;
        }
        CompetitionMatchEntity finalMatch = finalOpt.get();
        if (finalMatch.getStatus() != CompetitionMatchStatus.COMPLETED || !matchFullyWatched(competition, finalMatch)) {
            return competition;
        }
        competition.setChampionUserId(finalMatch.getWinnerUserId());
        competition.setStatus(CompetitionStatus.COMPLETED);
        competition.setTournamentPhase(TournamentPhase.REPLAYS);
        competition.setIntermissionEndsAt(null);
        return competitionRepository.save(competition);
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
