package dev.sportdraft.competition.control;

import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.simulation.control.DraftCompletedEvent;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class CompetitionModeRegistry {

    private final Map<GameMode, CompetitionInitializer> initializers = new EnumMap<>(GameMode.class);
    private final Map<GameMode, String> startedEventTypes = new EnumMap<>(GameMode.class);

    CompetitionModeRegistry(
        TournamentBracketService tournamentBracketService,
        LeagueFixtureService leagueFixtureService
    ) {
        initializers.put(GameMode.TOURNAMENT, tournamentBracketService::initializeTournament);
        initializers.put(GameMode.LEAGUE, leagueFixtureService::initializeLeague);
        startedEventTypes.put(GameMode.TOURNAMENT, "tournament_started");
        startedEventTypes.put(GameMode.LEAGUE, "league_started");
    }

    boolean supports(GameMode mode) {
        return initializers.containsKey(mode);
    }

    CompetitionEntity initialize(DraftCompletedEvent event) {
        CompetitionInitializer initializer = initializers.get(event.gameMode());
        if (initializer == null) {
            throw new IllegalStateException("Unsupported competition game mode: " + event.gameMode());
        }
        return initializer.initialize(event);
    }

    String startedEventType(GameMode mode) {
        return startedEventTypes.get(mode);
    }
}
