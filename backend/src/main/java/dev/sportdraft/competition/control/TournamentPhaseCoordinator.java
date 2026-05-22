package dev.sportdraft.competition.control;

import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchRepository;
import dev.sportdraft.competition.entity.CompetitionMatchStatus;
import dev.sportdraft.competition.entity.CompetitionMatchViewRepository;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.competition.entity.CompetitionStatus;
import dev.sportdraft.competition.entity.TournamentPhase;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TournamentPhaseCoordinator {

    private final CompetitionRepository competitionRepository;
    private final CompetitionMatchRepository matchRepository;
    private final CompetitionMatchViewRepository viewRepository;
    private final CompetitionJson competitionJson;

    public TournamentPhaseCoordinator(
        CompetitionRepository competitionRepository,
        CompetitionMatchRepository matchRepository,
        CompetitionMatchViewRepository viewRepository,
        CompetitionJson competitionJson
    ) {
        this.competitionRepository = competitionRepository;
        this.matchRepository = matchRepository;
        this.viewRepository = viewRepository;
        this.competitionJson = competitionJson;
    }

    @Transactional
    public boolean tryStartIntermissionIfReady(CompetitionEntity competition) {
        if (!TournamentPhaseMachine.canStartIntermission(competition.getTournamentPhase())) {
            return false;
        }
        if (!bothSemisSimulated(competition.getId()) || !allSemisWatchedByParticipants(competition.getId())) {
            return false;
        }
        ensureFinalMatch(competition);
        competition.setTournamentPhase(TournamentPhase.INTERMISSION);
        competition.setIntermissionEndsAt(Instant.now().plusSeconds(TournamentBracketService.INTERMISSION_SECONDS));
        competitionRepository.save(competition);
        return true;
    }

    @Transactional
    public boolean tryStartFinalPhase(CompetitionEntity competition) {
        if (!TournamentPhaseMachine.canAdvanceToFinal(
            competition.getTournamentPhase(),
            competition.getIntermissionEndsAt(),
            Instant.now()
        )) {
            return false;
        }
        competition.setTournamentPhase(TournamentPhase.FINAL);
        competition.setIntermissionEndsAt(null);
        competitionRepository.save(competition);
        return true;
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

        CompetitionDraftSnapshot snapshot = competitionJson.readSnapshot(competition.getDraftSnapshotJson());
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

    boolean allSemisWatchedByParticipants(UUID competitionId) {
        CompetitionMatchEntity semi1 = matchRepository.findByCompetitionIdAndRoundKey(competitionId, "SEMI_1").orElse(null);
        CompetitionMatchEntity semi2 = matchRepository.findByCompetitionIdAndRoundKey(competitionId, "SEMI_2").orElse(null);
        if (semi1 == null || semi2 == null) {
            return false;
        }
        return semiParticipantsWatched(semi1) && semiParticipantsWatched(semi2);
    }

    boolean bothSemisSimulated(UUID competitionId) {
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

    boolean semiParticipantsWatched(CompetitionMatchEntity match) {
        return viewRepository.existsByMatchIdAndUserId(match.getId(), match.getHomeUserId())
            && viewRepository.existsByMatchIdAndUserId(match.getId(), match.getAwayUserId());
    }

    private boolean matchFullyWatched(CompetitionEntity competition, CompetitionMatchEntity match) {
        long views = viewRepository.countByMatchId(match.getId());
        return views >= requiredWatchers(competition, match);
    }

    private int requiredWatchers(CompetitionEntity competition, CompetitionMatchEntity match) {
        if (TournamentRules.isSemi(match.getRoundKey())) {
            return 2;
        }
        return snapshotPlayerCount(competition);
    }

    private int snapshotPlayerCount(CompetitionEntity competition) {
        return competitionJson.readSnapshot(competition.getDraftSnapshotJson()).playerCount();
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
