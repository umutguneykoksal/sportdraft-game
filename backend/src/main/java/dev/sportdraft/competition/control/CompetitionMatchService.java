package dev.sportdraft.competition.control;

import dev.sportdraft.competition.boundary.CompetitionMatchOpenResponse;
import dev.sportdraft.competition.boundary.CompetitionResponse;
import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.competition.entity.CompetitionType;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompetitionMatchService {

    private final CompetitionRepository competitionRepository;
    private final CompetitionMatchCommandService commandService;

    public CompetitionMatchService(
        CompetitionRepository competitionRepository,
        CompetitionMatchCommandService commandService
    ) {
        this.competitionRepository = competitionRepository;
        this.commandService = commandService;
    }

    public CompetitionMatchOpenResponse openMatch(UUID sessionId, UUID matchId, UUID userId, boolean replay) {
        return commandService.openMatch(sessionId, matchId, userId, replay, loadType(sessionId));
    }

    public CompetitionResponse acknowledgeWatch(UUID sessionId, UUID matchId, UUID userId) {
        return commandService.acknowledgeWatch(sessionId, matchId, userId, loadType(sessionId));
    }

    private CompetitionType loadType(UUID sessionId) {
        return competitionRepository.findBySessionId(sessionId)
            .map(CompetitionEntity::getType)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Competition not found"));
    }
}
