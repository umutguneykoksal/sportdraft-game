package dev.sportdraft.simulation.control;

import dev.sportdraft.competition.control.CompetitionJson;
import dev.sportdraft.competition.control.CompetitionDraftSnapshot;
import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchRepository;
import dev.sportdraft.competition.entity.CompetitionMatchStatus;
import dev.sportdraft.simulation.entity.SimulationResult;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class CompetitionMatchSimulator {

    private final MatchSimulator matchSimulator;
    private final CompetitionJson competitionJson;
    private final CompetitionMatchRepository matchRepository;

    CompetitionMatchSimulator(
        MatchSimulator matchSimulator,
        CompetitionJson competitionJson,
        CompetitionMatchRepository matchRepository
    ) {
        this.matchSimulator = matchSimulator;
        this.competitionJson = competitionJson;
        this.matchRepository = matchRepository;
    }

    public SimulationResult simulateIfNeeded(
        CompetitionDraftSnapshot snapshot,
        CompetitionMatchEntity match,
        boolean allowDraws
    ) {
        if (match.getStatus() == CompetitionMatchStatus.COMPLETED) {
            return storedResult(match);
        }

        var byUser = snapshot.players().stream()
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
            away.userId(),
            allowDraws
        );

        match.setHomeScore(result.homeGoals());
        match.setAwayScore(result.awayGoals());
        match.setHomePenaltyScore(result.decidedOnPenalties() ? result.homePenaltyGoals() : null);
        match.setAwayPenaltyScore(result.decidedOnPenalties() ? result.awayPenaltyGoals() : null);
        match.setReplayDataJson(competitionJson.writeTimeline(result.timeline()));
        match.setWinnerUserId(
            allowDraws
                ? result.resolveWinnerOrNull(match.getHomeUserId(), match.getAwayUserId())
                : result.resolveWinner(match.getHomeUserId(), match.getAwayUserId())
        );
        match.setStatus(CompetitionMatchStatus.COMPLETED);
        matchRepository.save(match);

        return result;
    }

    private SimulationResult storedResult(CompetitionMatchEntity match) {
        return new SimulationResult(
            match.getHomeScore() != null ? match.getHomeScore() : 0,
            match.getAwayScore() != null ? match.getAwayScore() : 0,
            competitionJson.readTimeline(match.getReplayDataJson()),
            match.getHomePenaltyScore() != null ? match.getHomePenaltyScore() : 0,
            match.getAwayPenaltyScore() != null ? match.getAwayPenaltyScore() : 0
        );
    }
}
