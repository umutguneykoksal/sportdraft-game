package com.example.auction.competition.control;

import com.example.auction.competition.boundary.CompetitionMatchResponse;
import com.example.auction.competition.boundary.CompetitionResponse;
import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchRepository;
import com.example.auction.competition.entity.CompetitionMatchStatus;
import com.example.auction.competition.entity.CompetitionMatchViewRepository;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.competition.entity.CompetitionStatus;
import com.example.auction.competition.entity.TournamentPhase;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompetitionQueryService {

    private final CompetitionRepository competitionRepository;
    private final CompetitionMatchRepository matchRepository;
    private final CompetitionMatchViewRepository viewRepository;
    private final ActiveSessionStore sessionStore;
    private final TournamentBracketService bracketService;
    private final TournamentCompetitionRecoveryService competitionRecoveryService;

    public CompetitionQueryService(
        CompetitionRepository competitionRepository,
        CompetitionMatchRepository matchRepository,
        CompetitionMatchViewRepository viewRepository,
        ActiveSessionStore sessionStore,
        TournamentBracketService bracketService,
        TournamentCompetitionRecoveryService competitionRecoveryService
    ) {
        this.competitionRepository = competitionRepository;
        this.matchRepository = matchRepository;
        this.viewRepository = viewRepository;
        this.sessionStore = sessionStore;
        this.bracketService = bracketService;
        this.competitionRecoveryService = competitionRecoveryService;
    }

    public CompetitionResponse getBySessionId(UUID sessionId) {
        return getBySessionId(sessionId, null);
    }

    public CompetitionResponse getBySessionId(UUID sessionId, UUID viewerUserId) {
        CompetitionEntity competition = competitionRepository.findBySessionId(sessionId)
            .orElseGet(() -> {
                competitionRecoveryService.recoverIfMissing(sessionId);
                return competitionRepository.findBySessionId(sessionId).orElse(null);
            });
        if (competition == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Competition not found");
        }

        bracketService.ensureFinalMatch(competition);

        ActiveSession session = sessionStore.findById(sessionId).orElse(null);
        TournamentDraftSnapshot snapshot = bracketService.readSnapshot(competition);

        List<CompetitionMatchResponse> matchResponses = matchRepository
            .findByCompetitionIdOrderBySortOrderAsc(competition.getId())
            .stream()
            .map(m -> toMatchResponse(competition, m, viewerUserId, snapshot, session))
            .toList();

        boolean revealChampion = competition.getStatus() == CompetitionStatus.COMPLETED
            && viewerUserId != null
            && finalWatchedByViewer(competition, viewerUserId);

        return new CompetitionResponse(
            competition.getId(),
            competition.getSessionId(),
            competition.getType().name(),
            competition.getStatus().name(),
            competition.getTournamentPhase().name(),
            competition.getIntermissionEndsAt() != null ? competition.getIntermissionEndsAt().toString() : null,
            competition.getTournamentPhase() == TournamentPhase.REPLAYS,
            revealChampion ? competition.getChampionUserId() : null,
            revealChampion ? championNickname(snapshot, competition.getChampionUserId()) : null,
            matchResponses
        );
    }

    private CompetitionMatchResponse toMatchResponse(
        CompetitionEntity competition,
        CompetitionMatchEntity match,
        UUID viewerUserId,
        TournamentDraftSnapshot snapshot,
        ActiveSession session
    ) {
        boolean watchedByMe = viewerUserId != null
            && viewRepository.existsByMatchIdAndUserId(match.getId(), viewerUserId);
        boolean scoresVisible = bracketService.scoresVisibleForViewer(competition, match, watchedByMe);
        long watchCount = viewRepository.countByMatchId(match.getId());
        int required = bracketService.requiredWatchers(competition, match);

        return new CompetitionMatchResponse(
            match.getId(),
            match.getRoundKey(),
            match.getHomeUserId(),
            nickname(snapshot, session, match.getHomeUserId()),
            match.getAwayUserId(),
            nickname(snapshot, session, match.getAwayUserId()),
            scoresVisible ? match.getHomeScore() : null,
            scoresVisible ? match.getAwayScore() : null,
            scoresVisible ? match.getHomePenaltyScore() : null,
            scoresVisible ? match.getAwayPenaltyScore() : null,
            match.getStatus().name(),
            bracketService.isMandatoryWatchPending(competition, match, viewerUserId, watchedByMe),
            watchedByMe,
            bracketService.isReplayAllowed(competition, match),
            watchCount >= required,
            watchCount,
            required
        );
    }

    private boolean finalWatchedByViewer(CompetitionEntity competition, UUID viewerUserId) {
        return matchRepository.findByCompetitionIdAndRoundKey(competition.getId(), "FINAL")
            .map(finalMatch -> viewRepository.existsByMatchIdAndUserId(finalMatch.getId(), viewerUserId))
            .orElse(false);
    }

    private String nickname(
        TournamentDraftSnapshot snapshot,
        ActiveSession session,
        UUID userId
    ) {
        if (session != null) {
            String fromSession = session.findPlayerByUserId(userId).map(ActivePlayer::getNickname).orElse(null);
            if (fromSession != null) {
                return fromSession;
            }
        }
        return snapshot.players().stream()
            .filter(p -> p.userId().equals(userId))
            .map(TournamentDraftSnapshot.PlayerSnapshot::nickname)
            .findFirst()
            .orElse(null);
    }

    private String championNickname(TournamentDraftSnapshot snapshot, UUID championUserId) {
        if (championUserId == null) {
            return null;
        }
        return snapshot.players().stream()
            .filter(p -> p.userId().equals(championUserId))
            .map(TournamentDraftSnapshot.PlayerSnapshot::nickname)
            .findFirst()
            .orElse(null);
    }
}
