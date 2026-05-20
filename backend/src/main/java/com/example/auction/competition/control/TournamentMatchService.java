package com.example.auction.competition.control;

import com.example.auction.competition.boundary.CompetitionMatchOpenResponse;
import com.example.auction.competition.boundary.CompetitionResponse;
import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchRepository;
import com.example.auction.competition.entity.CompetitionMatchStatus;
import com.example.auction.competition.entity.CompetitionMatchViewEntity;
import com.example.auction.competition.entity.CompetitionMatchViewRepository;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.competition.entity.CompetitionStatus;
import com.example.auction.competition.entity.TournamentPhase;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.control.SessionAccess;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.simulation.entity.SimulationResult;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TournamentMatchService {

    private final CompetitionRepository competitionRepository;
    private final CompetitionMatchRepository matchRepository;
    private final CompetitionMatchViewRepository viewRepository;
    private final ActiveSessionStore sessionStore;
    private final TournamentBracketService bracketService;
    private final CompetitionQueryService competitionQueryService;
    private final SseEmitterRegistry sseEmitterRegistry;

    public TournamentMatchService(
        CompetitionRepository competitionRepository,
        CompetitionMatchRepository matchRepository,
        CompetitionMatchViewRepository viewRepository,
        ActiveSessionStore sessionStore,
        TournamentBracketService bracketService,
        CompetitionQueryService competitionQueryService,
        SseEmitterRegistry sseEmitterRegistry
    ) {
        this.competitionRepository = competitionRepository;
        this.matchRepository = matchRepository;
        this.viewRepository = viewRepository;
        this.sessionStore = sessionStore;
        this.bracketService = bracketService;
        this.competitionQueryService = competitionQueryService;
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    @Transactional
    public CompetitionMatchOpenResponse openMatch(UUID sessionId, UUID matchId, UUID userId, boolean replay) {
        CompetitionEntity competition = loadCompetition(sessionId);
        requireParticipant(competition, sessionId, userId);

        CompetitionMatchEntity match = loadMatch(competition.getId(), matchId);
        boolean watched = viewRepository.existsByMatchIdAndUserId(matchId, userId);

        if (replay) {
            if (!bracketService.isReplayAllowed(competition, match)) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Replays unlock after everyone watches the final"
                );
            }
        } else {
            if (competition.getTournamentPhase() == TournamentPhase.INTERMISSION) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Semi-final results — final starts shortly"
                );
            }
            if (match.getRoundKey().startsWith("SEMI") && !bracketService.isMatchParticipant(match, userId)) {
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only watch your own semi-final"
                );
            }
            if (!bracketService.isMandatoryWatchPending(competition, match, userId, watched)) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This match is not available to watch right now"
                );
            }
        }

        SimulationResult result = bracketService.simulateMatchIfNeeded(competition, match);
        TournamentDraftSnapshot snapshot = bracketService.readSnapshot(competition);
        int required = bracketService.requiredWatchers(competition, match);
        boolean showScores = replay || watched;

        return new CompetitionMatchOpenResponse(
            match.getId(),
            match.getRoundKey(),
            match.getHomeUserId(),
            nickname(snapshot, match.getHomeUserId()),
            match.getAwayUserId(),
            nickname(snapshot, match.getAwayUserId()),
            formation(snapshot, match.getHomeUserId()),
            formation(snapshot, match.getAwayUserId()),
            result.timeline(),
            showScores ? result.homeGoals() : null,
            showScores ? result.awayGoals() : null,
            showScores && result.decidedOnPenalties() ? result.homePenaltyGoals() : null,
            showScores && result.decidedOnPenalties() ? result.awayPenaltyGoals() : null,
            watched,
            replay,
            bracketService.matchFullyWatched(competition, match),
            viewRepository.countByMatchId(matchId),
            required
        );
    }

    @Transactional
    public CompetitionResponse acknowledgeWatch(UUID sessionId, UUID matchId, UUID userId) {
        CompetitionEntity competition = loadCompetition(sessionId);
        requireParticipant(competition, sessionId, userId);

        CompetitionMatchEntity match = loadMatch(competition.getId(), matchId);
        if (match.getStatus() != CompetitionMatchStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Match has not been simulated yet");
        }
        if (competition.getTournamentPhase() == TournamentPhase.REPLAYS) {
            return competitionQueryService.getBySessionId(sessionId, userId);
        }

        if (!viewRepository.existsByMatchIdAndUserId(matchId, userId)) {
            CompetitionMatchViewEntity view = new CompetitionMatchViewEntity();
            view.setMatchId(matchId);
            view.setUserId(userId);
            view.setWatchedAt(Instant.now());
            viewRepository.save(view);
        }

        if (bracketService.tryStartIntermissionIfReady(competition)) {
            competition = competitionRepository.findById(competition.getId()).orElse(competition);
            sseEmitterRegistry.broadcast(
                sessionId,
                "tournament_intermission",
                Map.of(
                    "sessionId", sessionId,
                    "competitionId", competition.getId(),
                    "intermissionEndsAt", competition.getIntermissionEndsAt(),
                    "tournamentPhase", TournamentPhase.INTERMISSION.name()
                )
            );
        }

        CompetitionEntity updated = bracketService.tryCompleteTournament(competition);
        updated = competitionRepository.findById(updated.getId()).orElse(updated);

        if (updated.getStatus() == CompetitionStatus.COMPLETED) {
            finishSessionIfPresent(sessionId);
            sseEmitterRegistry.broadcast(
                sessionId,
                "tournament_completed",
                Map.of(
                    "sessionId", sessionId,
                    "competitionId", updated.getId(),
                    "championUserId", updated.getChampionUserId(),
                    "tournamentPhase", TournamentPhase.REPLAYS.name()
                )
            );
        } else {
            sseEmitterRegistry.broadcast(
                sessionId,
                "tournament_updated",
                Map.of(
                    "sessionId", sessionId,
                    "competitionId", updated.getId(),
                    "tournamentPhase", updated.getTournamentPhase().name()
                )
            );
        }

        return competitionQueryService.getBySessionId(sessionId, userId);
    }

    private void requireParticipant(CompetitionEntity competition, UUID sessionId, UUID userId) {
        sessionStore.findById(sessionId).ifPresentOrElse(
            session -> SessionAccess.requireMember(session, userId),
            () -> requireSnapshotMember(competition, userId)
        );
    }

    private void requireSnapshotMember(CompetitionEntity competition, UUID userId) {
        boolean member = bracketService.readSnapshot(competition).players().stream()
            .anyMatch(p -> p.userId().equals(userId));
        if (!member) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Player not found in session");
        }
    }

    private void finishSessionIfPresent(UUID sessionId) {
        sessionStore.findById(sessionId).ifPresent(session -> {
            long vBefore = session.getVersion();
            session.moveToFinished();
            sessionStore.saveIfVersionMatch(session, vBefore);
        });
    }

    private CompetitionEntity loadCompetition(UUID sessionId) {
        return competitionRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Competition not found"));
    }

    private CompetitionMatchEntity loadMatch(UUID competitionId, UUID matchId) {
        CompetitionMatchEntity match = matchRepository.findById(matchId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));
        if (!match.getCompetitionId().equals(competitionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found");
        }
        return match;
    }

    private static String nickname(TournamentDraftSnapshot snapshot, UUID userId) {
        return snapshot.players().stream()
            .filter(p -> p.userId().equals(userId))
            .map(TournamentDraftSnapshot.PlayerSnapshot::nickname)
            .findFirst()
            .orElse("Player");
    }

    private static String formation(TournamentDraftSnapshot snapshot, UUID userId) {
        return snapshot.players().stream()
            .filter(p -> p.userId().equals(userId))
            .map(p -> p.formation().name())
            .findFirst()
            .orElse("FOUR_FOUR_TWO");
    }
}
