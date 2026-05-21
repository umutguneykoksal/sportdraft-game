package com.example.auction.competition.control;

import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchRepository;
import com.example.auction.competition.entity.CompetitionMatchStatus;
import com.example.auction.competition.entity.CompetitionMatchViewRepository;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.competition.entity.CompetitionStatus;
import com.example.auction.competition.entity.CompetitionType;
import com.example.auction.competition.entity.LeaguePhase;
import com.example.auction.simulation.control.DraftCompletedEvent;
import com.example.auction.simulation.control.CompetitionMatchSimulator;
import com.example.auction.simulation.entity.SimulationResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeagueFixtureService {

    public static final long INTERMISSION_SECONDS = 15;

    private static final String[] ROUND_ROBIN_KEYS = {
        "MD1_A", "MD1_B", "MD2_A", "MD2_B", "MD3_A", "MD3_B"
    };
    private static final int[][] ROUND_ROBIN_PAIR_INDEXES = {
        {0, 1}, {2, 3}, {0, 2}, {1, 3}, {0, 3}, {1, 2}
    };

    private final CompetitionRepository competitionRepository;
    private final CompetitionMatchRepository matchRepository;
    private final CompetitionMatchViewRepository viewRepository;
    private final CompetitionMatchSimulator matchSimulator;
    private final CompetitionJson competitionJson;
    private final LeagueStandingsService standingsService;

    public LeagueFixtureService(
        CompetitionRepository competitionRepository,
        CompetitionMatchRepository matchRepository,
        CompetitionMatchViewRepository viewRepository,
        CompetitionMatchSimulator matchSimulator,
        CompetitionJson competitionJson,
        LeagueStandingsService standingsService
    ) {
        this.competitionRepository = competitionRepository;
        this.matchRepository = matchRepository;
        this.viewRepository = viewRepository;
        this.matchSimulator = matchSimulator;
        this.competitionJson = competitionJson;
        this.standingsService = standingsService;
    }

    @Transactional
    public CompetitionEntity initializeLeague(DraftCompletedEvent event) {
        if (event.players().size() != 4) {
            throw new IllegalStateException("League requires exactly 4 players");
        }

        CompetitionEntity competition = new CompetitionEntity();
        competition.setId(UUID.randomUUID());
        competition.setSessionId(event.sessionId());
        competition.setType(CompetitionType.LEAGUE);
        competition.setStatus(CompetitionStatus.IN_PROGRESS);
        competition.setLeaguePhase(LeaguePhase.MATCHDAY_1);
        competition.setCreatedAt(Instant.now());
        competition.setDraftSnapshotJson(competitionJson.writeSnapshot(TournamentDraftSnapshot.from(event)));
        competitionRepository.save(competition);

        List<UUID> order = event.players().stream().map(DraftCompletedEvent.DraftPlayerRoster::userId).toList();
        long seed = event.seed();
        for (int i = 0; i < ROUND_ROBIN_KEYS.length; i++) {
            int[] pair = ROUND_ROBIN_PAIR_INDEXES[i];
            matchRepository.save(createMatch(
                competition.getId(),
                ROUND_ROBIN_KEYS[i],
                order.get(pair[0]),
                order.get(pair[1]),
                seed + i,
                i + 1
            ));
        }

        return competition;
    }

    @Transactional
    public SimulationResult simulateMatchIfNeeded(CompetitionEntity competition, CompetitionMatchEntity match) {
        boolean allowDraws = !LeagueRules.isPlayoff(match.getRoundKey());
        return simulateMatchIfNeeded(competition, match, allowDraws);
    }

    @Transactional
    public SimulationResult simulateMatchIfNeeded(
        CompetitionEntity competition,
        CompetitionMatchEntity match,
        boolean allowDraws
    ) {
        return matchSimulator.simulateIfNeeded(readSnapshot(competition), match, allowDraws);
    }

    public boolean isMandatoryWatchPending(
        CompetitionEntity competition,
        CompetitionMatchEntity match,
        UUID viewerUserId,
        boolean watchedByMe
    ) {
        return LeagueRules.isMandatoryWatchPending(
            competition.getLeaguePhase(),
            match,
            viewerUserId,
            watchedByMe
        );
    }

    public boolean isActiveMatchdayMatch(CompetitionEntity competition, CompetitionMatchEntity match) {
        return LeagueRules.belongsToMatchday(match.getRoundKey(), competition.getLeaguePhase().matchdayNumber());
    }

    public boolean isReplayAllowed(CompetitionEntity competition, CompetitionMatchEntity match) {
        return LeagueRules.canReplay(competition.getLeaguePhase(), match.getStatus());
    }

    public boolean scoresVisibleForViewer(
        CompetitionEntity competition,
        CompetitionMatchEntity match,
        boolean watchedByMe
    ) {
        if (match.getStatus() != CompetitionMatchStatus.COMPLETED) {
            return false;
        }
        return LeagueRules.scoresVisibleForViewer(competition.getLeaguePhase(), match.getRoundKey(), watchedByMe);
    }

    public boolean isMatchPlayable(CompetitionEntity competition, CompetitionMatchEntity match, UUID viewerUserId) {
        if (viewerUserId == null) {
            return match.getStatus() == CompetitionMatchStatus.COMPLETED;
        }
        boolean watched = viewRepository.existsByMatchIdAndUserId(match.getId(), viewerUserId);
        return isMandatoryWatchPending(competition, match, viewerUserId, watched);
    }

    public boolean isMatchParticipant(CompetitionMatchEntity match, UUID userId) {
        return LeagueRules.isMatchParticipant(match, userId);
    }

    public boolean matchParticipantsWatched(CompetitionMatchEntity match) {
        return viewRepository.existsByMatchIdAndUserId(match.getId(), match.getHomeUserId())
            && viewRepository.existsByMatchIdAndUserId(match.getId(), match.getAwayUserId());
    }

    public boolean matchdayReadyToAdvance(CompetitionEntity competition, int matchday) {
        List<CompetitionMatchEntity> dayMatches = matchRepository.findByCompetitionIdOrderBySortOrderAsc(competition.getId())
            .stream()
            .filter(m -> LeagueRules.belongsToMatchday(m.getRoundKey(), matchday))
            .toList();
        if (dayMatches.size() != 2) {
            return false;
        }
        return dayMatches.stream().allMatch(m -> m.getStatus() == CompetitionMatchStatus.COMPLETED)
            && dayMatches.stream().allMatch(this::matchParticipantsWatched);
    }

    @Transactional
    public boolean tryStartMatchdayBreak(CompetitionEntity competition) {
        LeaguePhase phase = competition.getLeaguePhase();
        int md = phase.matchdayNumber();
        if (!LeaguePhaseMachine.canEvaluateMatchdayBreak(phase)) {
            return false;
        }
        if (!matchdayReadyToAdvance(competition, md)) {
            return false;
        }
        if (md >= 3) {
            return tryResolveAfterRoundRobin(competition);
        }
        competition.setLeaguePhase(phase.breakAfterMatchday());
        competition.setIntermissionEndsAt(Instant.now().plusSeconds(INTERMISSION_SECONDS));
        competitionRepository.save(competition);
        return true;
    }

    @Transactional
    public boolean tryAdvanceFromBreak(CompetitionEntity competition) {
        LeaguePhase phase = competition.getLeaguePhase();
        if (!LeaguePhaseMachine.canAdvanceFromBreak(phase, competition.getIntermissionEndsAt(), Instant.now())) {
            return false;
        }
        competition.setLeaguePhase(phase.nextMatchdayPhase());
        competition.setIntermissionEndsAt(null);
        competitionRepository.save(competition);
        return true;
    }

    @Transactional
    public boolean tryResolveAfterRoundRobin(CompetitionEntity competition) {
        if (competition.getLeaguePhase() != LeaguePhase.MATCHDAY_3) {
            return false;
        }
        if (!matchdayReadyToAdvance(competition, 3)) {
            return false;
        }

        TournamentDraftSnapshot snapshot = readSnapshot(competition);
        List<CompetitionMatchEntity> matches = matchRepository.findByCompetitionIdOrderBySortOrderAsc(competition.getId());
        LeagueStandingsService.ChampionshipResolution resolution =
            standingsService.resolveChampionship(snapshot, matches);

        return switch (resolution) {
            case LeagueStandingsService.ChampionshipResolution.InProgress ignored -> false;
            case LeagueStandingsService.ChampionshipResolution.SingleChampion sc ->
                finalizeSingleChampion(competition, sc.userId());
            case LeagueStandingsService.ChampionshipResolution.CoChampions cc ->
                finalizeCoChampions(competition, cc.userIds());
            case LeagueStandingsService.ChampionshipResolution.PlayoffRequired pr ->
                startPlayoff(competition, pr, snapshot);
        };
    }

    @Transactional
    public CompetitionEntity tryCompletePlayoff(CompetitionEntity competition) {
        if (competition.getLeaguePhase() != LeaguePhase.CHAMPIONSHIP_PLAYOFF) {
            return competition;
        }
        CompetitionMatchEntity playoff = matchRepository
            .findByCompetitionIdAndRoundKey(competition.getId(), LeagueRules.PLAYOFF_ROUND_KEY)
            .orElse(null);
        if (playoff == null
            || playoff.getStatus() != CompetitionMatchStatus.COMPLETED
            || !matchParticipantsWatched(playoff)) {
            return competition;
        }
        LeagueCompletion.markSingleChampion(competition, playoff.getWinnerUserId());
        return competitionRepository.save(competition);
    }

    @Transactional
    public void createPlayoffFinal(CompetitionEntity competition, UUID homeUserId, UUID awayUserId, long seed) {
        if (matchRepository.findByCompetitionIdAndRoundKey(competition.getId(), LeagueRules.PLAYOFF_ROUND_KEY)
            .isPresent()) {
            return;
        }
        matchRepository.save(createMatch(
            competition.getId(),
            LeagueRules.PLAYOFF_ROUND_KEY,
            homeUserId,
            awayUserId,
            seed,
            7
        ));
        competition.setLeaguePhase(LeaguePhase.CHAMPIONSHIP_PLAYOFF);
        competition.setIntermissionEndsAt(null);
        competitionRepository.save(competition);
    }

    public int requiredWatchers(CompetitionEntity competition, CompetitionMatchEntity match) {
        return LeagueRules.PARTICIPANTS_PER_MATCH;
    }

    public boolean matchFullyWatched(CompetitionEntity competition, CompetitionMatchEntity match) {
        return viewRepository.countByMatchId(match.getId()) >= requiredWatchers(competition, match);
    }

    public TournamentDraftSnapshot readSnapshot(CompetitionEntity competition) {
        return competitionJson.readSnapshot(competition.getDraftSnapshotJson());
    }

    public List<UUID> readCoChampionIds(CompetitionEntity competition) {
        return competitionJson.readUuidList(competition.getCoChampionUserIdsJson());
    }

    private boolean finalizeSingleChampion(CompetitionEntity competition, UUID championUserId) {
        LeagueCompletion.markSingleChampion(competition, championUserId);
        competitionRepository.save(competition);
        return true;
    }

    private boolean finalizeCoChampions(CompetitionEntity competition, List<UUID> userIds) {
        LeagueCompletion.markCoChampions(competition, competitionJson, userIds);
        competitionRepository.save(competition);
        return true;
    }

    private boolean startPlayoff(
        CompetitionEntity competition,
        LeagueStandingsService.ChampionshipResolution.PlayoffRequired pr,
        TournamentDraftSnapshot snapshot
    ) {
        createPlayoffFinal(competition, pr.homeUserId(), pr.awayUserId(), snapshot.seed() + 6);
        return true;
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
