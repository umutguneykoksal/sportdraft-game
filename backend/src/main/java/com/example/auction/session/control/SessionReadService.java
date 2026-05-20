package com.example.auction.session.control;

import com.example.auction.bidding.control.AuctionService;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.boundary.SpectateStateResponse;
import com.example.auction.session.entity.MatchHistory;
import com.example.auction.session.entity.MatchHistoryRepository;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.simulation.boundary.MatchResultResponse;
import com.example.auction.simulation.control.MatchResultRecoveryService;
import com.example.auction.simulation.entity.MatchEvent;
import com.example.auction.simulation.entity.MatchEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SessionReadService {

    private static final Logger log = LoggerFactory.getLogger(SessionReadService.class);

    private final ActiveSessionStore sessionStore;
    private final MatchHistoryRepository matchHistoryRepository;
    private final MatchResultRecoveryService matchResultRecoveryService;
    private final AuctionService auctionService;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final ObjectMapper redisObjectMapper;

    public SessionReadService(
        ActiveSessionStore sessionStore,
        MatchHistoryRepository matchHistoryRepository,
        MatchResultRecoveryService matchResultRecoveryService,
        AuctionService auctionService,
        SseEmitterRegistry sseEmitterRegistry,
        @Qualifier("redisObjectMapper") ObjectMapper redisObjectMapper
    ) {
        this.sessionStore = sessionStore;
        this.matchHistoryRepository = matchHistoryRepository;
        this.matchResultRecoveryService = matchResultRecoveryService;
        this.auctionService = auctionService;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.redisObjectMapper = redisObjectMapper;
    }

    public SpectateStateResponse spectate(UUID sessionId) {
        ActiveSession session = sessionStore.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        var snap = auctionService.buildSpectateSnapshot(session);
        return new SpectateStateResponse(
            snap.status(), snap.currentRound(),
            snap.homeTeam(), snap.awayTeam(),
            snap.homeFormation(), snap.awayFormation(),
            snap.homeUserId(), snap.awayUserId(),
            snap.homeNickname(), snap.awayNickname(),
            snap.homeBudget(), snap.awayBudget(),
            snap.homeScore(), snap.awayScore()
        );
    }

    public ResponseEntity<List<MatchEvent>> getSpectateMatchTimeline(UUID sessionId) {
        ActiveSession session = sessionStore.findById(sessionId).orElse(null);
        if (session != null) {
            matchResultRecoveryService.recoverIfMissing(sessionId);
            session = sessionStore.findById(sessionId).orElse(null);
            if (session != null) {
                List<MatchEvent> timeline = session.getMatchTimeline();
                if (timeline != null && !timeline.isEmpty()) {
                    return ResponseEntity.ok(timeline);
                }
            }
        }

        return matchHistoryRepository.findBySessionId(sessionId)
            .map(this::extractTimelineFromArchiveForSpectate)
            .orElse(ResponseEntity.notFound().build());
    }

    public ResponseEntity<MatchResultResponse> getMatchResult(UUID sessionId, UUID userId) {
        ActiveSession session = sessionStore.findById(sessionId).orElse(null);
        if (session != null) {
            SessionAccess.requireMember(session, userId);
            matchResultRecoveryService.recoverIfMissing(sessionId);
            session = sessionStore.findById(sessionId).orElse(null);
            if (session != null && session.getHomeScore() != null) {
                return ResponseEntity.ok(buildResultFromActiveSession(session, userId));
            }
        }

        return matchHistoryRepository.findBySessionId(sessionId)
            .map(h -> buildResultFromArchive(h, userId))
            .orElse(ResponseEntity.notFound().build());
    }

    public void recordPing(UUID sessionId, UUID userId) {
        sseEmitterRegistry.recordPlayerPing(sessionId, userId);
    }

    public ResponseEntity<List<MatchEvent>> getMatchTimeline(UUID sessionId, UUID userId) {
        ActiveSession session = sessionStore.findById(sessionId).orElse(null);
        if (session != null) {
            SessionAccess.requireMember(session, userId);
            List<MatchEvent> timeline = session.getMatchTimeline();
            if (timeline != null && !timeline.isEmpty()) {
                return ResponseEntity.ok(timeline);
            }
        }

        return matchHistoryRepository.findBySessionId(sessionId)
            .map(h -> extractTimelineFromArchive(h, userId))
            .orElse(ResponseEntity.notFound().build());
    }

    private MatchResultResponse buildResultFromActiveSession(ActiveSession session, UUID userId) {
        List<ActivePlayer> sorted = session.playersSortedById();
        boolean isHome = !sorted.isEmpty() && sorted.get(0).getUserId().equals(userId);
        int myScore = isHome ? session.getHomeScore() : session.getAwayScore();
        int oppScore = isHome ? session.getAwayScore() : session.getHomeScore();
        String homeNick = sorted.isEmpty() ? "Home" : sorted.get(0).getNickname();
        String awayNick = sorted.size() > 1 ? sorted.get(1).getNickname() : "Away";
        String myNickname = isHome ? homeNick : awayNick;
        String oppNickname = isHome ? awayNick : homeNick;
        UUID homeUserId = sorted.isEmpty() ? userId : sorted.get(0).getUserId();
        UUID awayUserId = sorted.size() > 1 ? sorted.get(1).getUserId() : userId;
        AuctionService.TeamSnapshot teams = auctionService.buildTeamSnapshot(session, userId);
        int[] stats = calculateStats(session.getMatchTimeline(), userId);

        int sessionHomePens = session.getHomePenaltyScore() != null ? session.getHomePenaltyScore() : 0;
        int sessionAwayPens = session.getAwayPenaltyScore() != null ? session.getAwayPenaltyScore() : 0;
        int myPens = isHome ? sessionHomePens : sessionAwayPens;
        int oppPens = isHome ? sessionAwayPens : sessionHomePens;
        UUID winnerId = session.getMatchWinnerUserId();

        return new MatchResultResponse(
            myScore, oppScore,
            teams.myTeam(), teams.opponentTeam(),
            teams.myFormation(), teams.opponentFormation(),
            myNickname, oppNickname,
            userId.toString(),
            stats[0], stats[1], stats[2], stats[3],
            homeUserId.toString(), awayUserId.toString(),
            homeNick, awayNick,
            session.getHomeScore(), session.getAwayScore(),
            sessionHomePens, sessionAwayPens,
            myPens, oppPens,
            winnerId != null ? winnerId.toString() : homeUserId.toString()
        );
    }

    private int[] calculateStats(List<MatchEvent> timeline, UUID userId) {
        if (timeline == null || timeline.isEmpty()) {
            return new int[]{0, 0, 0, 0};
        }

        Set<MatchEventType> shotTypes = Set.of(
            MatchEventType.GOAL, MatchEventType.SHOT_SAVED, MatchEventType.SHOT_MISSED
        );

        int myShots = 0;
        int oppShots = 0;
        int myFouls = 0;
        int oppFouls = 0;

        for (MatchEvent e : timeline) {
            boolean isMine = userId.equals(e.teamId());
            if (shotTypes.contains(e.type())) {
                if (isMine) {
                    myShots++;
                } else {
                    oppShots++;
                }
            }
            if (e.type() == MatchEventType.FOUL) {
                if (isMine) {
                    myFouls++;
                } else {
                    oppFouls++;
                }
            }
        }

        return new int[]{myShots, oppShots, myFouls, oppFouls};
    }

    private ResponseEntity<MatchResultResponse> buildResultFromArchive(MatchHistory h, UUID userId) {
        boolean isHome = h.getHomeUserId().equals(userId);
        boolean isAway = h.getAwayUserId().equals(userId);
        if (!isHome && !isAway) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Player not found in session");
        }
        int myScore = isHome ? h.getHomeScore() : h.getAwayScore();
        int oppScore = isHome ? h.getAwayScore() : h.getHomeScore();

        try {
            ActiveSession archived = redisObjectMapper.readValue(h.getReplayData(), ActiveSession.class);
            List<ActivePlayer> sorted = archived.playersSortedById();
            String homeNick = sorted.isEmpty() ? "Home" : sorted.get(0).getNickname();
            String awayNick = sorted.size() > 1 ? sorted.get(1).getNickname() : "Away";
            String myNickname = isHome ? homeNick : awayNick;
            String oppNickname = isHome ? awayNick : homeNick;
            AuctionService.TeamSnapshot teams = auctionService.buildTeamSnapshot(archived, userId);
            int[] stats = calculateStats(archived.getMatchTimeline(), userId);
            int sessionHomePens = archived.getHomePenaltyScore() != null ? archived.getHomePenaltyScore() : 0;
            int sessionAwayPens = archived.getAwayPenaltyScore() != null ? archived.getAwayPenaltyScore() : 0;
            int myPens = isHome ? sessionHomePens : sessionAwayPens;
            int oppPens = isHome ? sessionAwayPens : sessionHomePens;
            UUID winnerId = archived.getMatchWinnerUserId();

            return ResponseEntity.ok(new MatchResultResponse(
                myScore, oppScore,
                teams.myTeam(), teams.opponentTeam(),
                teams.myFormation(), teams.opponentFormation(),
                myNickname, oppNickname,
                userId.toString(),
                stats[0], stats[1], stats[2], stats[3],
                h.getHomeUserId().toString(), h.getAwayUserId().toString(),
                homeNick, awayNick,
                h.getHomeScore(), h.getAwayScore(),
                sessionHomePens, sessionAwayPens,
                myPens, oppPens,
                winnerId != null ? winnerId.toString() : h.getHomeUserId().toString()
            ));
        } catch (Exception e) {
            log.warn("Failed to deserialize replay_data for session {}, returning scores only", h.getSessionId(), e);
            return ResponseEntity.ok(new MatchResultResponse(
                myScore, oppScore,
                List.of(), List.of(),
                isHome ? h.getHomeFormation() : h.getAwayFormation(),
                isHome ? h.getAwayFormation() : h.getHomeFormation(),
                null, null,
                userId.toString(),
                0, 0, 0, 0,
                h.getHomeUserId().toString(), h.getAwayUserId().toString(),
                "Home", "Away",
                h.getHomeScore(), h.getAwayScore(),
                0, 0, 0, 0,
                myScore > oppScore ? userId.toString() : h.getAwayUserId().toString()
            ));
        }
    }

    private ResponseEntity<List<MatchEvent>> extractTimelineFromArchiveForSpectate(MatchHistory h) {
        try {
            ActiveSession archived = redisObjectMapper.readValue(h.getReplayData(), ActiveSession.class);
            List<MatchEvent> timeline = archived.getMatchTimeline();
            if (timeline != null && !timeline.isEmpty()) {
                return ResponseEntity.ok(timeline);
            }
            return ResponseEntity.ok(List.of());
        } catch (Exception e) {
            log.warn("Failed to deserialize replay_data for spectate timeline, session {}", h.getSessionId(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    private ResponseEntity<List<MatchEvent>> extractTimelineFromArchive(MatchHistory h, UUID userId) {
        if (!h.getHomeUserId().equals(userId) && !h.getAwayUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Player not found in session");
        }
        try {
            ActiveSession archived = redisObjectMapper.readValue(h.getReplayData(), ActiveSession.class);
            List<MatchEvent> timeline = archived.getMatchTimeline();
            if (timeline != null && !timeline.isEmpty()) {
                return ResponseEntity.ok(timeline);
            }
            return ResponseEntity.ok(List.of());
        } catch (Exception e) {
            log.warn("Failed to deserialize replay_data for timeline, session {}", h.getSessionId(), e);
            return ResponseEntity.ok(List.of());
        }
    }
}
