package com.example.auction.session.control;

import com.example.auction.session.redis.ActiveSessionStore;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SessionAdminService {

    private final ActiveSessionStore sessionStore;

    public SessionAdminService(ActiveSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public void deleteSession(UUID sessionId) {
        if (!sessionStore.exists(sessionId)) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        sessionStore.delete(sessionId);
    }

    public Map<String, Integer> deleteActiveSessions() {
        int count = sessionStore.deleteAllActive();
        return Map.of("deletedCount", count);
    }
}
