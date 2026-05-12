package com.example.auction.session.control;

import com.example.auction.session.entity.MatchHistory;
import com.example.auction.session.entity.MatchHistoryRepository;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.SessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Archives a finished ActiveSession to PostgreSQL match_history table,
 * then deletes it from Redis.
 */
@Component
public class SessionArchiver {

    private static final Logger log = LoggerFactory.getLogger(SessionArchiver.class);

    private final ActiveSessionStore sessionStore;
    private final MatchHistoryRepository matchHistoryRepository;
    private final ObjectMapper objectMapper;

    public SessionArchiver(
        ActiveSessionStore sessionStore,
        MatchHistoryRepository matchHistoryRepository,
        @Qualifier("redisObjectMapper") ObjectMapper objectMapper
    ) {
        this.sessionStore = sessionStore;
        this.matchHistoryRepository = matchHistoryRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void archive(ActiveSession session) {
        if (matchHistoryRepository.existsBySessionId(session.getId())) {
            log.debug("Session {} already archived, skipping", session.getId());
            sessionStore.delete(session.getId());
            return;
        }

        List<ActivePlayer> sorted = session.playersSortedById();
        if (sorted.size() < 2) {
            log.warn("Session {} has fewer than 2 players, cannot archive", session.getId());
            return;
        }

        ActivePlayer home = sorted.get(0);
        ActivePlayer away = sorted.get(1);
        String replayJson;
        try {
            replayJson = objectMapper.writeValueAsString(session);
        } catch (Exception e) {
            log.error("Failed to serialize session {} for archival", session.getId(), e);
            return;
        }

        MatchHistory history = new MatchHistory(
            UUID.randomUUID(),
            session.getId(),
            home.getUserId(),
            away.getUserId(),
            session.getHomeScore() != null ? session.getHomeScore() : 0,
            session.getAwayScore() != null ? session.getAwayScore() : 0,
            home.getFormation().name(),
            away.getFormation().name(),
            session.getMatchSeed() != null ? session.getMatchSeed() : 0L,
            Instant.now(),
            replayJson
        );

        matchHistoryRepository.save(history);
        sessionStore.delete(session.getId());
        log.info("Archived session {} to match_history", session.getId());
    }
}
