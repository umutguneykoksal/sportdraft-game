package dev.sportdraft.competition.control;

import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchRepository;
import dev.sportdraft.competition.entity.CompetitionMatchStatus;
import dev.sportdraft.competition.entity.CompetitionMatchViewRepository;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.competition.entity.CompetitionStatus;
import dev.sportdraft.competition.entity.CompetitionType;
import dev.sportdraft.competition.entity.TournamentPhase;
import dev.sportdraft.simulation.control.CompetitionMatchSimulator;
import dev.sportdraft.simulation.control.DraftCompletedEvent;
import dev.sportdraft.simulation.entity.SimulationResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TournamentBracketService {

    public static final long INTERMISSION_SECONDS = 15;

    private final CompetitionRepository competitionRepository;
    private final CompetitionMatchRepository matchRepository;
    private final CompetitionMatchViewRepository viewRepository;
    private final CompetitionMatchSimulator matchSimulator;
    private final CompetitionJson competitionJson;
    private final TournamentBracketFlow bracketFlow;

    public TournamentBracketService(
        CompetitionRepository competitionRepository,
        CompetitionMatchRepository matchRepository,
        CompetitionMatchViewRepository viewRepository,
        CompetitionMatchSimulator matchSimulator,
        CompetitionJson competitionJson,
        TournamentBracketFlow bracketFlow
    ) {
        this.competitionRepository = competitionRepository;
        this.matchRepository = matchRepository;
        this.viewRepository = viewRepository;
        this.matchSimulator = matchSimulator;
        this.competitionJson = competitionJson;
        this.bracketFlow = bracketFlow;
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
        competition.setDraftSnapshotJson(competitionJson.writeSnapshot(CompetitionDraftSnapshot.from(event)));
        competitionRepository.save(competition);

        List<UUID> order = event.players().stream().map(DraftCompletedEvent.DraftPlayerRoster::userId).toList();
        matchRepository.save(createMatch(competition.getId(), "SEMI_1", order.get(0), order.get(1), event.seed(), 1));
        matchRepository.save(createMatch(competition.getId(), "SEMI_2", order.get(2), order.get(3), event.seed() + 1, 2));

        return competition;
    }

    @Transactional
    public SimulationResult simulateMatchIfNeeded(CompetitionEntity competition, CompetitionMatchEntity match) {
        SimulationResult result = matchSimulator.simulateIfNeeded(readSnapshot(competition), match, false);
        bracketFlow.ensureFinalMatch(competition);
        return result;
    }

    public boolean isMandatoryWatchPending(
        CompetitionEntity competition,
        CompetitionMatchEntity match,
        UUID viewerUserId,
        boolean watchedByMe
    ) {
        return TournamentRules.isMandatoryWatchPending(
            competition.getTournamentPhase(),
            match,
            viewerUserId,
            watchedByMe,
            isTournamentParticipant(competition, viewerUserId)
        );
    }

    public boolean isReplayAllowed(CompetitionEntity competition, CompetitionMatchEntity match) {
        return TournamentRules.canReplay(competition.getTournamentPhase(), match.getStatus());
    }

    public boolean scoresVisibleForViewer(
        CompetitionEntity competition,
        CompetitionMatchEntity match,
        boolean watchedByMe
    ) {
        if (match.getStatus() != CompetitionMatchStatus.COMPLETED) {
            return false;
        }
        return TournamentRules.scoresVisibleForViewer(
            competition.getTournamentPhase(),
            match.getRoundKey(),
            watchedByMe
        );
    }

    public boolean allSemisWatchedByParticipants(UUID competitionId) {
        return bracketFlow.allSemisWatchedByParticipants(competitionId);
    }

    @Transactional
    public boolean tryStartIntermissionIfReady(CompetitionEntity competition) {
        return bracketFlow.tryStartIntermissionIfReady(competition);
    }

    @Transactional
    public boolean tryStartFinalPhase(CompetitionEntity competition) {
        return bracketFlow.tryStartFinalPhase(competition);
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

    public boolean isTournamentParticipant(CompetitionEntity competition, UUID userId) {
        return readSnapshot(competition).players().stream().anyMatch(p -> p.userId().equals(userId));
    }

    public boolean isMatchParticipant(CompetitionMatchEntity match, UUID userId) {
        return TournamentRules.isMatchParticipant(match, userId);
    }

    public boolean semiParticipantsWatched(CompetitionMatchEntity match) {
        return bracketFlow.semiParticipantsWatched(match);
    }

    public boolean semisReadyForFinal(UUID competitionId) {
        return bothSemisSimulated(competitionId);
    }

    public boolean bothSemisSimulated(UUID competitionId) {
        return bracketFlow.bothSemisSimulated(competitionId);
    }

    public int requiredWatchers(CompetitionEntity competition, CompetitionMatchEntity match) {
        if (TournamentRules.isSemi(match.getRoundKey())) {
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
        bracketFlow.ensureFinalMatch(competition);
    }

    @Transactional
    public CompetitionEntity tryCompleteTournament(CompetitionEntity competition) {
        return bracketFlow.tryCompleteTournament(competition);
    }

    public int snapshotPlayerCount(CompetitionEntity competition) {
        return competitionJson.readSnapshot(competition.getDraftSnapshotJson()).playerCount();
    }

    public CompetitionDraftSnapshot readSnapshot(CompetitionEntity competition) {
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
