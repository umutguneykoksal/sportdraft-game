package com.example.auction.competition.control;

import com.example.auction.competition.boundary.CompetitionMatchResponse;
import com.example.auction.competition.boundary.CompetitionResponse;
import com.example.auction.competition.boundary.LeagueStandingRow;
import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchRepository;
import com.example.auction.competition.entity.CompetitionMatchStatus;
import com.example.auction.competition.entity.CompetitionMatchViewRepository;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.competition.entity.CompetitionStatus;
import com.example.auction.competition.entity.CompetitionType;
import com.example.auction.competition.entity.LeaguePhase;
import com.example.auction.competition.entity.TournamentPhase;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.util.ArrayList;
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
    private final LeagueFixtureService leagueFixtureService;
    private final LeagueStandingsService leagueStandingsService;
    private final TournamentCompetitionRecoveryService tournamentRecoveryService;
    private final LeagueCompetitionRecoveryService leagueRecoveryService;
    private final LeagueVisibilityPolicy leagueVisibilityPolicy;
    private final TournamentVisibilityPolicy tournamentVisibilityPolicy;

    public CompetitionQueryService(
        CompetitionRepository competitionRepository,
        CompetitionMatchRepository matchRepository,
        CompetitionMatchViewRepository viewRepository,
        ActiveSessionStore sessionStore,
        TournamentBracketService bracketService,
        LeagueFixtureService leagueFixtureService,
        LeagueStandingsService leagueStandingsService,
        TournamentCompetitionRecoveryService tournamentRecoveryService,
        LeagueCompetitionRecoveryService leagueRecoveryService,
        LeagueVisibilityPolicy leagueVisibilityPolicy,
        TournamentVisibilityPolicy tournamentVisibilityPolicy
    ) {
        this.competitionRepository = competitionRepository;
        this.matchRepository = matchRepository;
        this.viewRepository = viewRepository;
        this.sessionStore = sessionStore;
        this.bracketService = bracketService;
        this.leagueFixtureService = leagueFixtureService;
        this.leagueStandingsService = leagueStandingsService;
        this.tournamentRecoveryService = tournamentRecoveryService;
        this.leagueRecoveryService = leagueRecoveryService;
        this.leagueVisibilityPolicy = leagueVisibilityPolicy;
        this.tournamentVisibilityPolicy = tournamentVisibilityPolicy;
    }

    public CompetitionResponse getBySessionId(UUID sessionId) {
        return getBySessionId(sessionId, null);
    }

    public CompetitionResponse getBySessionId(UUID sessionId, UUID viewerUserId) {
        CompetitionEntity competition = competitionRepository.findBySessionId(sessionId)
            .orElseGet(() -> {
                tournamentRecoveryService.recoverIfMissing(sessionId);
                leagueRecoveryService.recoverIfMissing(sessionId);
                return competitionRepository.findBySessionId(sessionId).orElse(null);
            });
        if (competition == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Competition not found");
        }

        if (competition.getType() == CompetitionType.LEAGUE) {
            return buildLeagueResponse(competition, viewerUserId);
        }
        return buildTournamentResponse(competition, viewerUserId);
    }

    private CompetitionResponse buildTournamentResponse(CompetitionEntity competition, UUID viewerUserId) {
        bracketService.ensureFinalMatch(competition);

        ActiveSession session = sessionStore.findById(competition.getSessionId()).orElse(null);
        CompetitionDraftSnapshot snapshot = bracketService.readSnapshot(competition);

        List<CompetitionMatchResponse> matchResponses = matchRepository
            .findByCompetitionIdOrderBySortOrderAsc(competition.getId())
            .stream()
            .map(m -> toMatchResponse(competition, m, viewerUserId, snapshot, session, tournamentVisibilityPolicy))
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
            matchResponses,
            null,
            List.of(),
            List.of(),
            List.of()
        );
    }

    private CompetitionResponse buildLeagueResponse(CompetitionEntity competition, UUID viewerUserId) {
        ActiveSession session = sessionStore.findById(competition.getSessionId()).orElse(null);
        CompetitionDraftSnapshot snapshot = leagueFixtureService.readSnapshot(competition);
        List<CompetitionMatchEntity> allMatches =
            matchRepository.findByCompetitionIdOrderBySortOrderAsc(competition.getId());

        List<CompetitionMatchResponse> matchResponses = allMatches.stream()
            .map(m -> toMatchResponse(competition, m, viewerUserId, snapshot, session, leagueVisibilityPolicy))
            .toList();

        List<LeagueStandingRow> standings = leagueStandingsService.computeStandings(snapshot, allMatches);
        boolean revealChampions = competition.getStatus() == CompetitionStatus.COMPLETED
            && (competition.getLeaguePhase() == LeaguePhase.REPLAYS || competition.getLeaguePhase() == LeaguePhase.COMPLETED);

        List<UUID> coIds = revealChampions ? leagueFixtureService.readCoChampionIds(competition) : List.of();
        List<String> coNames = revealChampions
            ? coIds.stream().map(id -> championNickname(snapshot, id)).toList()
            : List.of();

        return new CompetitionResponse(
            competition.getId(),
            competition.getSessionId(),
            competition.getType().name(),
            competition.getStatus().name(),
            null,
            competition.getIntermissionEndsAt() != null ? competition.getIntermissionEndsAt().toString() : null,
            competition.getLeaguePhase() == LeaguePhase.REPLAYS,
            revealChampions ? competition.getChampionUserId() : null,
            revealChampions ? championNickname(snapshot, competition.getChampionUserId()) : null,
            matchResponses,
            competition.getLeaguePhase().name(),
            standings,
            coIds,
            coNames
        );
    }

    private CompetitionMatchResponse toMatchResponse(
        CompetitionEntity competition,
        CompetitionMatchEntity match,
        UUID viewerUserId,
        CompetitionDraftSnapshot snapshot,
        ActiveSession session,
        CompetitionVisibilityPolicy policy
    ) {
        boolean watchedByMe = viewerUserId != null
            && viewRepository.existsByMatchIdAndUserId(match.getId(), viewerUserId);
        boolean scoresVisible = policy.scoresVisible(competition, match, watchedByMe);
        long watchCount = viewRepository.countByMatchId(match.getId());
        int required = policy.requiredWatchers(competition, match);

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
            policy.mandatoryWatchPending(competition, match, viewerUserId, watchedByMe),
            watchedByMe,
            policy.replayAllowed(competition, match),
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
        CompetitionDraftSnapshot snapshot,
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
            .map(CompetitionDraftSnapshot.PlayerSnapshot::nickname)
            .findFirst()
            .orElse(null);
    }

    private String championNickname(CompetitionDraftSnapshot snapshot, UUID championUserId) {
        if (championUserId == null) {
            return null;
        }
        return snapshot.players().stream()
            .filter(p -> p.userId().equals(championUserId))
            .map(CompetitionDraftSnapshot.PlayerSnapshot::nickname)
            .findFirst()
            .orElse(null);
    }
}
