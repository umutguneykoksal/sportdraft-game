package dev.sportdraft.common.sse;

import dev.sportdraft.common.realtime.RealtimeConnectionAuthorizer;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping({"/api/sessions", "/api/v2/sessions"})
public class SseStreamController {

    private final SseEmitterRegistry sseEmitterRegistry;
    private final RealtimeConnectionAuthorizer connectionAuthorizer;

    public SseStreamController(
        SseEmitterRegistry sseEmitterRegistry,
        RealtimeConnectionAuthorizer connectionAuthorizer
    ) {
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.connectionAuthorizer = connectionAuthorizer;
    }

    @GetMapping(value = "/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
        @PathVariable UUID sessionId,
        @RequestParam("token") String token,
        @RequestParam(value = "spectator", required = false, defaultValue = "false") boolean spectator,
        jakarta.servlet.http.HttpServletResponse response
    ) {
        // Prevent Nginx / Railway reverse proxies from buffering the SSE stream.
        // Without this, events may be withheld until the buffer fills or the connection closes.
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");

        UUID userId = parseToken(token);
        if (!spectator) {
            // Non-spectators must be members of the session; spectators can observe any session.
            connectionAuthorizer.assertSessionMembership(sessionId, userId);
        }
        return sseEmitterRegistry.register(sessionId, spectator, userId);
    }

    private UUID parseToken(String token) {
        try {
            return UUID.fromString(token);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
    }
}
