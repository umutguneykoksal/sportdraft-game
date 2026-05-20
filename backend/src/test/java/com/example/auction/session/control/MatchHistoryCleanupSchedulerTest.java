package com.example.auction.session.control;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auction.session.entity.MatchHistoryRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchHistoryCleanupSchedulerTest {

    @Mock
    private MatchHistoryRepository matchHistoryRepository;

    @Test
    void purgeStaleHistory_deletesRowsOlderThanRetention() {
        MatchHistoryCleanupScheduler scheduler =
            new MatchHistoryCleanupScheduler(matchHistoryRepository, true, 7);
        when(matchHistoryRepository.deleteByPlayedAtBefore(any(Instant.class))).thenReturn(3);

        scheduler.purgeStaleHistory();

        verify(matchHistoryRepository).deleteByPlayedAtBefore(any(Instant.class));
    }

    @Test
    void purgeStaleHistory_skipsWhenDisabled() {
        MatchHistoryCleanupScheduler scheduler =
            new MatchHistoryCleanupScheduler(matchHistoryRepository, false, 7);

        scheduler.purgeStaleHistory();

        verify(matchHistoryRepository, never()).deleteByPlayedAtBefore(any(Instant.class));
    }
}
