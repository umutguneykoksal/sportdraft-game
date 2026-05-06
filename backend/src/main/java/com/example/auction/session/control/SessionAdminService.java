package com.example.auction.session.control;

import com.example.auction.session.entity.GameSession;
import com.example.auction.session.entity.GameSessionRepository;
import com.example.auction.session.entity.SessionStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class SessionAdminService {

    private final GameSessionRepository gameSessionRepository;

    public SessionAdminService(GameSessionRepository gameSessionRepository) {
        this.gameSessionRepository = gameSessionRepository;
    }

    public void deleteSession(UUID sessionId) {
        if (!gameSessionRepository.existsById(sessionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        gameSessionRepository.deleteById(sessionId);
    }

    public Map<String, Integer> deleteActiveSessions() {
        List<GameSession> activeSessions = gameSessionRepository.findByStatusNot(SessionStatus.FINISHED);
        gameSessionRepository.deleteAll(activeSessions);
        return Map.of("deletedCount", activeSessions.size());
    }
}
