package com.example.auction.common.sse;

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
@RequestMapping("/api/sessions")
public class SseStreamController {

    private final SseEmitterRegistry sseEmitterRegistry;

    public SseStreamController(SseEmitterRegistry sseEmitterRegistry) {
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    @GetMapping(value = "/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
        @PathVariable UUID sessionId,
        @RequestParam("token") String token,
        @RequestParam(value = "spectator", required = false, defaultValue = "false") boolean spectator
    ) {
        validateToken(token);
        return sseEmitterRegistry.register(sessionId, spectator);
    }

    private void validateToken(String token) {
        try {
            UUID.fromString(token);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
    }
}
