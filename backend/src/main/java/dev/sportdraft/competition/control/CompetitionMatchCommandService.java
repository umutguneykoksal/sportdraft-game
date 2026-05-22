package dev.sportdraft.competition.control;

import dev.sportdraft.competition.boundary.CompetitionMatchOpenResponse;
import dev.sportdraft.competition.boundary.CompetitionResponse;
import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionMatchEntity;
import dev.sportdraft.competition.entity.CompetitionMatchRepository;
import dev.sportdraft.competition.entity.CompetitionMatchStatus;
import dev.sportdraft.competition.entity.CompetitionMatchViewEntity;
import dev.sportdraft.competition.entity.CompetitionMatchViewRepository;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.competition.entity.CompetitionType;
import dev.sportdraft.session.control.SessionAccess;
import dev.sportdraft.session.redis.ActiveSessionStore;
import dev.sportdraft.simulation.entity.SimulationResult;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class CompetitionMatchCommandService {

    private final CompetitionRepository competitionRepository;
    private final CompetitionMatchRepository matchRepository;
    private final CompetitionMatchViewRepository viewRepository;
    private final ActiveSessionStore sessionStore;
    private final CompetitionQueryService competitionQueryService;
    private final Map<CompetitionType, CompetitionMatchModeHooks> hooksByType;

    CompetitionMatchCommandService(
        CompetitionRepository competitionRepository,
        CompetitionMatchRepository matchRepository,
        CompetitionMatchViewRepository viewRepository,
        ActiveSessionStore sessionStore,
        CompetitionQueryService competitionQueryService,
        List<CompetitionMatchModeHooks> hooks
    ) {
        this.competitionRepository = competitionRepository;
        this.matchRepository = matchRepository;
        this.viewRepository = viewRepository;
        this.sessionStore = sessionStore;
        this.competitionQueryService = competitionQueryService;
        this.hooksByType = new EnumMap<>(CompetitionType.class);
        for (CompetitionMatchModeHooks hook : hooks) {
            this.hooksByType.put(hook.type(), hook);
        }
    }

    @Transactional
    public CompetitionMatchOpenResponse openMatch(
        UUID sessionId,
        UUID matchId,
        UUID userId,
        boolean replay,
        CompetitionType expectedType
    ) {
        CompetitionEntity competition = loadCompetition(sessionId, expectedType);
        CompetitionMatchModeHooks hooks = hooksFor(expectedType);
        requireParticipant(competition, sessionId, userId, hooks);

        CompetitionMatchEntity match = loadMatch(competition.getId(), matchId);
        boolean watched = viewRepository.existsByMatchIdAndUserId(matchId, userId);
        hooks.validateOpen(competition, match, userId, replay, watched);

        SimulationResult result = hooks.simulateIfNeeded(competition, match);
        CompetitionDraftSnapshot snapshot = hooks.readSnapshot(competition);
        int required = hooks.requiredWatchers(competition, match);
        boolean showScores = replay || watched;

        return new CompetitionMatchOpenResponse(
            match.getId(),
            match.getRoundKey(),
            match.getHomeUserId(),
            CompetitionMatchSupport.nickname(snapshot, match.getHomeUserId()),
            match.getAwayUserId(),
            CompetitionMatchSupport.nickname(snapshot, match.getAwayUserId()),
            CompetitionMatchSupport.formation(snapshot, match.getHomeUserId()),
            CompetitionMatchSupport.formation(snapshot, match.getAwayUserId()),
            result.timeline(),
            showScores ? result.homeGoals() : null,
            showScores ? result.awayGoals() : null,
            showScores && result.decidedOnPenalties() ? result.homePenaltyGoals() : null,
            showScores && result.decidedOnPenalties() ? result.awayPenaltyGoals() : null,
            watched,
            replay,
            hooks.matchFullyWatched(competition, match),
            viewRepository.countByMatchId(matchId),
            required
        );
    }

    @Transactional
    public CompetitionResponse acknowledgeWatch(
        UUID sessionId,
        UUID matchId,
        UUID userId,
        CompetitionType expectedType
    ) {
        CompetitionEntity competition = loadCompetition(sessionId, expectedType);
        CompetitionMatchModeHooks hooks = hooksFor(expectedType);
        requireParticipant(competition, sessionId, userId, hooks);

        CompetitionMatchEntity match = loadMatch(competition.getId(), matchId);
        if (match.getStatus() != CompetitionMatchStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Match has not been simulated yet");
        }
        if (hooks.isReplaysPhase(competition)) {
            return competitionQueryService.getBySessionId(sessionId, userId);
        }

        recordWatchIfNeeded(matchId, userId);
        competition = hooks.afterWatchRecorded(sessionId, competition);
        return competitionQueryService.getBySessionId(sessionId, userId);
    }

    private void recordWatchIfNeeded(UUID matchId, UUID userId) {
        if (!viewRepository.existsByMatchIdAndUserId(matchId, userId)) {
            CompetitionMatchViewEntity view = new CompetitionMatchViewEntity();
            view.setMatchId(matchId);
            view.setUserId(userId);
            view.setWatchedAt(Instant.now());
            viewRepository.save(view);
        }
    }

    private void requireParticipant(
        CompetitionEntity competition,
        UUID sessionId,
        UUID userId,
        CompetitionMatchModeHooks hooks
    ) {
        sessionStore.findById(sessionId).ifPresentOrElse(
            session -> SessionAccess.requireMember(session, userId),
            () -> {
                boolean member = hooks.readSnapshot(competition).players().stream()
                    .anyMatch(p -> p.userId().equals(userId));
                if (!member) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Player not found in session");
                }
            }
        );
    }

    private CompetitionEntity loadCompetition(UUID sessionId, CompetitionType expectedType) {
        return competitionRepository.findBySessionId(sessionId)
            .filter(c -> c.getType() == expectedType)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                expectedType == CompetitionType.LEAGUE ? "League competition not found" : "Competition not found"
            ));
    }

    private CompetitionMatchEntity loadMatch(UUID competitionId, UUID matchId) {
        CompetitionMatchEntity match = matchRepository.findById(matchId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));
        if (!match.getCompetitionId().equals(competitionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found");
        }
        return match;
    }

    private CompetitionMatchModeHooks hooksFor(CompetitionType type) {
        CompetitionMatchModeHooks hooks = hooksByType.get(type);
        if (hooks == null) {
            throw new IllegalStateException("No match hooks registered for " + type);
        }
        return hooks;
    }
}
