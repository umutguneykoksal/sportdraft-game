package dev.sportdraft.common.realtime;

import dev.sportdraft.session.redis.ActiveSessionStore;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RealtimeConnectionAuthorizer {

    private final ActiveSessionStore sessionStore;

    public RealtimeConnectionAuthorizer(ActiveSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public void assertSessionMembership(UUID sessionId, UUID userId) {
        boolean member = sessionStore.findById(sessionId)
            .map(session -> session.hasPlayerWithUserId(userId))
            .orElse(false);
        if (!member) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token is not part of this session");
        }
    }
}
