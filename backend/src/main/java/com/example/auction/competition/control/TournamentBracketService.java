package com.example.auction.competition.control;

import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchRepository;
import com.example.auction.competition.entity.CompetitionMatchStatus;
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
    private final MatchSimulator matchSimulator;

    public TournamentBracketService(
        CompetitionRepository competitionRepository,
        CompetitionMatchRepository matchRepository,
        MatchSimulator matchSimulator
    ) {
        this.competitionRepository = competitionRepository;
        this.matchRepository = matchRepository;
        this.matchSimulator = matchSimulator;
    }

    @Transactional
    public CompetitionEntity runTournament(DraftCompletedEvent event) {
        if (event.players().size() != 4) {
            throw new IllegalStateException("Tournament requires exactly 4 players");
        }

        CompetitionEntity competition = new CompetitionEntity();
        competition.setId(UUID.randomUUID());
        competition.setSessionId(event.sessionId());
        competition.setType(CompetitionType.TOURNAMENT);
        competition.setStatus(CompetitionStatus.IN_PROGRESS);
        competition.setCreatedAt(Instant.now());
        competitionRepository.save(competition);

        Map<UUID, DraftCompletedEvent.DraftPlayerRoster> byUser = event.players().stream()
            .collect(Collectors.toMap(DraftCompletedEvent.DraftPlayerRoster::userId, Function.identity()));

        List<UUID> order = event.players().stream().map(DraftCompletedEvent.DraftPlayerRoster::userId).toList();
        CompetitionMatchEntity semi1 = createMatch(competition.getId(), "SEMI_1", order.get(0), order.get(1), event.seed(), 1);
        CompetitionMatchEntity semi2 = createMatch(competition.getId(), "SEMI_2", order.get(2), order.get(3), event.seed() + 1, 2);
        matchRepository.save(semi1);
        matchRepository.save(semi2);

        UUID semi1Winner = simulateAndComplete(semi1, byUser);
        UUID semi2Winner = simulateAndComplete(semi2, byUser);

        CompetitionMatchEntity finalMatch = createMatch(
            competition.getId(), "FINAL", semi1Winner, semi2Winner, event.seed() + 2, 3
        );
        matchRepository.save(finalMatch);
        UUID champion = simulateAndComplete(finalMatch, byUser);

        competition.setChampionUserId(champion);
        competition.setStatus(CompetitionStatus.COMPLETED);
        return competitionRepository.save(competition);
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

    private UUID simulateAndComplete(
        CompetitionMatchEntity match,
        Map<UUID, DraftCompletedEvent.DraftPlayerRoster> rosters
    ) {
        DraftCompletedEvent.DraftPlayerRoster home = rosters.get(match.getHomeUserId());
        DraftCompletedEvent.DraftPlayerRoster away = rosters.get(match.getAwayUserId());

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
        match.setStatus(CompetitionMatchStatus.COMPLETED);
        matchRepository.save(match);

        if (result.homeGoals() > result.awayGoals()) {
            return home.userId();
        }
        if (result.awayGoals() > result.homeGoals()) {
            return away.userId();
        }
        return match.getSeed() % 2 == 0 ? home.userId() : away.userId();
    }
}
