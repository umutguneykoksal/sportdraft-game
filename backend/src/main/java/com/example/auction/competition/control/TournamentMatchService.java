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
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.control.SessionAccess;
import com.example.auction.session.redis.ActiveSession;
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
    public CompetitionMatchOpenResponse openMatch(UUID sessionId, UUID matchId, UUID userId) {
        CompetitionEntity competition = loadCompetition(sessionId);
        ActiveSession session = requireSession(sessionId);
        SessionAccess.requireMember(session, userId);

        CompetitionMatchEntity match = loadMatch(competition.getId(), matchId);
        if (!bracketService.isMatchPlayable(competition, match)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Final is locked until every player has watched both semi-finals"
            );
        }

        SimulationResult result = bracketService.simulateMatchIfNeeded(competition, match);
        boolean watched = viewRepository.existsByMatchIdAndUserId(matchId, userId);
        TournamentDraftSnapshot snapshot = bracketService.readSnapshot(competition);

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
            watched ? result.homeGoals() : null,
            watched ? result.awayGoals() : null,
            watched && result.decidedOnPenalties() ? result.homePenaltyGoals() : null,
            watched && result.decidedOnPenalties() ? result.awayPenaltyGoals() : null,
            watched,
            bracketService.allPlayersWatched(competition, match),
            viewRepository.countByMatchId(matchId),
            bracketService.snapshotPlayerCount(competition)
        );
    }

    @Transactional
    public CompetitionResponse acknowledgeWatch(UUID sessionId, UUID matchId, UUID userId) {
        CompetitionEntity competition = loadCompetition(sessionId);
        ActiveSession session = requireSession(sessionId);
        SessionAccess.requireMember(session, userId);

        CompetitionMatchEntity match = loadMatch(competition.getId(), matchId);
        if (match.getStatus() != CompetitionMatchStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Match has not been simulated yet");
        }

        if (!viewRepository.existsByMatchIdAndUserId(matchId, userId)) {
            CompetitionMatchViewEntity view = new CompetitionMatchViewEntity();
            view.setMatchId(matchId);
            view.setUserId(userId);
            view.setWatchedAt(Instant.now());
            viewRepository.save(view);
        }

        bracketService.ensureFinalMatch(competition);
        CompetitionEntity updated = bracketService.tryCompleteTournament(competition);

        if (updated.getStatus() == CompetitionStatus.COMPLETED) {
            sseEmitterRegistry.broadcast(
                sessionId,
                "tournament_completed",
                Map.of(
                    "sessionId", sessionId,
                    "competitionId", updated.getId(),
                    "championUserId", updated.getChampionUserId()
                )
            );
        } else {
            sseEmitterRegistry.broadcast(
                sessionId,
                "tournament_updated",
                Map.of("sessionId", sessionId, "competitionId", updated.getId())
            );
        }

        return competitionQueryService.getBySessionId(sessionId, userId);
    }

    private ActiveSession requireSession(UUID sessionId) {
        return sessionStore.getById(sessionId);
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
