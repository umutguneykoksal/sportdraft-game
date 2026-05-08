package com.example.auction.common.realtime;

import com.example.auction.session.entity.SessionPlayerRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RealtimeConnectionAuthorizer {

    private final SessionPlayerRepository sessionPlayerRepository;

    public RealtimeConnectionAuthorizer(SessionPlayerRepository sessionPlayerRepository) {
        this.sessionPlayerRepository = sessionPlayerRepository;
    }

    public void assertSessionMembership(UUID sessionId, UUID userId) {
        if (!sessionPlayerRepository.existsBySession_IdAndUserId(sessionId, userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token is not part of this session");
        }
    }
}
