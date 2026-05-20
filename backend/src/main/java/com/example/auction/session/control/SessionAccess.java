package com.example.auction.session.control;

import com.example.auction.session.redis.ActiveSession;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class SessionAccess {

    private SessionAccess() {
    }

    public static void requireMember(ActiveSession session, UUID userId) {
        if (!session.hasPlayerWithUserId(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Player not found in session");
        }
    }
}
