package com.example.auction.simulation.control;

import com.example.auction.session.entity.GameSession;
import com.example.auction.session.entity.GameSessionRepository;
import com.example.auction.session.entity.SessionPlayer;
import com.example.auction.session.entity.SessionPlayerRepository;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.simulation.entity.MatchResultRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MatchResultRecoveryScheduler {

    private final GameSessionRepository gameSessionRepository;
    private final SessionPlayerRepository sessionPlayerRepository;
    private final MatchResultRepository matchResultRepository;
    private final ApplicationEventPublisher publisher;

    public MatchResultRecoveryScheduler(
        GameSessionRepository gameSessionRepository,
        SessionPlayerRepository sessionPlayerRepository,
        MatchResultRepository matchResultRepository,
        ApplicationEventPublisher publisher
    ) {
        this.gameSessionRepository = gameSessionRepository;
        this.sessionPlayerRepository = sessionPlayerRepository;
        this.matchResultRepository = matchResultRepository;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void recoverMissingMatchResults() {
        recoverByStatus(SessionStatus.FINALIZING);
        recoverByStatus(SessionStatus.AWAITING_RESULT);
    }

    private void recoverByStatus(SessionStatus status) {
        List<GameSession> sessions = gameSessionRepository.findByStatusOrderByCreatedAtAsc(status);
        for (GameSession session : sessions) {
            UUID sessionId = session.getId();
            if (matchResultRepository.findBySession_Id(sessionId).isPresent()) {
                continue;
            }
            List<SessionPlayer> players = sessionPlayerRepository.findBySession_Id(sessionId);
            if (players.size() < 2) {
                continue;
            }
            players = players.stream()
                .sorted(Comparator.comparing(SessionPlayer::getUserId))
                .toList();
            SessionPlayer home = players.get(0);
            SessionPlayer away = players.get(1);
            if (home.getCards().size() < 11 || away.getCards().size() < 11) {
                continue;
            }

            publisher.publishEvent(
                new SessionFinalizedEvent(
                    UUID.randomUUID(),
                    Instant.now(),
                    sessionId,
                    session.getMatchSeed() == null ? sessionId.getMostSignificantBits() : session.getMatchSeed(),
                    home.getFormation(),
                    away.getFormation(),
                    home.getCards().stream()
                        .map(card -> new SessionFinalizedEvent.RatedCard(card.getId(), card.getPosition(), card.getRating()))
                        .toList(),
                    away.getCards().stream()
                        .map(card -> new SessionFinalizedEvent.RatedCard(card.getId(), card.getPosition(), card.getRating()))
                        .toList()
                )
            );
        }
    }
}
