package com.example.auction.session.control;

import com.example.auction.session.entity.MatchHistoryRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes archived match replays older than the configured retention window
 * so PostgreSQL does not grow unbounded on {@code match_history.replay_data}.
 */
@Component
public class MatchHistoryCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(MatchHistoryCleanupScheduler.class);

    private final MatchHistoryRepository matchHistoryRepository;
    private final boolean cleanupEnabled;
    private final int retentionDays;

    public MatchHistoryCleanupScheduler(
        MatchHistoryRepository matchHistoryRepository,
        @Value("${app.match-history.cleanup-enabled:true}") boolean cleanupEnabled,
        @Value("${app.match-history.retention-days:7}") int retentionDays
    ) {
        this.matchHistoryRepository = matchHistoryRepository;
        this.cleanupEnabled = cleanupEnabled;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Scheduled(cron = "${app.match-history.cleanup-cron:0 0 4 * * *}")
    @Transactional
    public void purgeStaleHistory() {
        if (!cleanupEnabled) {
            return;
        }
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = matchHistoryRepository.deleteByPlayedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Purged {} match_history row(s) older than {} days", deleted, retentionDays);
        }
    }
}
