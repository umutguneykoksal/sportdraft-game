package com.example.auction.competition.control;

import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchRepository;
import com.example.auction.competition.entity.CompetitionMatchStatus;
import com.example.auction.competition.entity.CompetitionMatchViewRepository;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.competition.entity.LeaguePhase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaguePhaseCoordinator {

    private final CompetitionRepository competitionRepository;
    private final CompetitionMatchRepository matchRepository;
    private final CompetitionMatchViewRepository viewRepository;
    private final LeagueStandingsService standingsService;
    private final CompetitionJson competitionJson;

    public LeaguePhaseCoordinator(
        CompetitionRepository competitionRepository,
        CompetitionMatchRepository matchRepository,
        CompetitionMatchViewRepository viewRepository,
        LeagueStandingsService standingsService,
        CompetitionJson competitionJson
    ) {
        this.competitionRepository = competitionRepository;
        this.matchRepository = matchRepository;
        this.viewRepository = viewRepository;
        this.standingsService = standingsService;
        this.competitionJson = competitionJson;
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
        competition.setIntermissionEndsAt(Instant.now().plusSeconds(LeagueFixtureService.INTERMISSION_SECONDS));
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

        CompetitionDraftSnapshot snapshot = readSnapshot(competition);
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

    boolean matchdayReadyToAdvance(CompetitionEntity competition, int matchday) {
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

    boolean matchParticipantsWatched(CompetitionMatchEntity match) {
        return viewRepository.existsByMatchIdAndUserId(match.getId(), match.getHomeUserId())
            && viewRepository.existsByMatchIdAndUserId(match.getId(), match.getAwayUserId());
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
        CompetitionDraftSnapshot snapshot
    ) {
        createPlayoffFinal(competition, pr.homeUserId(), pr.awayUserId(), snapshot.seed() + 6);
        return true;
    }

    private CompetitionDraftSnapshot readSnapshot(CompetitionEntity competition) {
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
