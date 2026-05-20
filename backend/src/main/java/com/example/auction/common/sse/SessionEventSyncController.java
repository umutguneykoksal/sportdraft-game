package com.example.auction.common.sse;

import com.example.auction.common.realtime.RealtimeConnectionAuthorizer;
import com.example.auction.common.realtime.SessionEventLog;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Allows clients to replay events they may have missed during a connection gap.
 * The event log is a bounded ring buffer (default 10_000 events, configurable) with TTL;
 * cleared when the session is archived.
 *
 * Response contains events ordered ascending by seq, or an empty list if the
 * requested seq is too old (client should fall back to a full snapshot fetch).
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionEventSyncController {

    private final SessionEventLog eventLog;
    private final RealtimeConnectionAuthorizer connectionAuthorizer;

    public SessionEventSyncController(
        SessionEventLog eventLog,
        RealtimeConnectionAuthorizer connectionAuthorizer
    ) {
        this.eventLog = eventLog;
        this.connectionAuthorizer = connectionAuthorizer;
    }

    @GetMapping("/{sessionId}/events/sync")
    public SyncResponse sync(
        @PathVariable UUID sessionId,
        @RequestParam("token") String token,
        @RequestParam("afterSeq") long afterSeq,
        @RequestParam(value = "spectator", required = false, defaultValue = "false") boolean spectator
    ) {
        UUID userId = UUID.fromString(token);
        if (!spectator) {
            connectionAuthorizer.assertSessionMembership(sessionId, userId);
        }
        List<Map<String, Object>> events = eventLog.since(sessionId, afterSeq);
        long lastSeq = eventLog.currentSeq(sessionId);
        return new SyncResponse(events, lastSeq);
    }

    public record SyncResponse(List<Map<String, Object>> events, long lastSeq) {}
}
