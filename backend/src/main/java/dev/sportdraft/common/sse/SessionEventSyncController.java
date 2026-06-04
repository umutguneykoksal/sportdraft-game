package dev.sportdraft.common.sse;

import dev.sportdraft.common.realtime.RealtimeConnectionAuthorizer;
import dev.sportdraft.common.realtime.SessionEventLog;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;
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
@RequestMapping({"/api/sessions", "/api/v2/sessions"})
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
        UUID userId = parseToken(token);
        if (!spectator) {
            connectionAuthorizer.assertSessionMembership(sessionId, userId);
        }
        List<Map<String, Object>> events = eventLog.since(sessionId, afterSeq);
        long lastSeq = eventLog.currentSeq(sessionId);
        return new SyncResponse(events, lastSeq);
    }

    private UUID parseToken(String token) {
        try {
            return UUID.fromString(token);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
    }

    public record SyncResponse(List<Map<String, Object>> events, long lastSeq) {}
}
