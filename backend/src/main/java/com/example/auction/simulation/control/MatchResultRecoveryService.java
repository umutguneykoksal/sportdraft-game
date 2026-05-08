package com.example.auction.simulation.control;

import com.example.auction.session.entity.GameSession;
import com.example.auction.session.entity.GameSessionRepository;
import com.example.auction.session.entity.SessionPlayer;
import com.example.auction.session.entity.SessionPlayerRepository;
import com.example.auction.session.entity.SessionStatus;
import com.example.auction.simulation.entity.MatchResult;
import com.example.auction.simulation.entity.MatchResultRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchResultRecoveryService {

    private final GameSessionRepository gameSessionRepository;
    private final SessionPlayerRepository sessionPlayerRepository;
    private final MatchResultRepository matchResultRepository;
    private final MatchSimulator matchSimulator;
    private final ApplicationEventPublisher publisher;

    public MatchResultRecoveryService(
        GameSessionRepository gameSessionRepository,
        SessionPlayerRepository sessionPlayerRepository,
        MatchResultRepository matchResultRepository,
        MatchSimulator matchSimulator,
        ApplicationEventPublisher publisher
    ) {
        this.gameSessionRepository = gameSessionRepository;
        this.sessionPlayerRepository = sessionPlayerRepository;
        this.matchResultRepository = matchResultRepository;
        this.matchSimulator = matchSimulator;
        this.publisher = publisher;
    }

    @Transactional
    public Optional<MatchResult> recoverIfMissing(UUID sessionId) {
        Optional<MatchResult> existing = matchResultRepository.findBySession_Id(sessionId);
        if (existing.isPresent()) {
            return existing;
        }

        GameSession session = gameSessionRepository.lockById(sessionId).orElse(null);
        if (session == null) {
            return Optional.empty();
        }
        if (session.getStatus() != SessionStatus.FINALIZING && session.getStatus() != SessionStatus.AWAITING_RESULT) {
            return Optional.empty();
        }

        List<SessionPlayer> players = sessionPlayerRepository.findBySession_Id(sessionId).stream()
            .sorted(Comparator.comparing(SessionPlayer::getUserId))
            .toList();
        if (players.size() < 2 || players.get(0).getCards().size() < 11 || players.get(1).getCards().size() < 11) {
            return Optional.empty();
        }

        SessionPlayer home = players.get(0);
        SessionPlayer away = players.get(1);
        long seed = session.getMatchSeed() == null ? sessionId.getMostSignificantBits() : session.getMatchSeed();

        MatchSimulator.Score score = matchSimulator.simulate(
            seed,
            home.getFormation(),
            away.getFormation(),
            home.getCards().stream()
                .map(card -> new SessionFinalizedEvent.RatedCard(card.getId(), card.getPosition(), card.getRating()))
                .toList(),
            away.getCards().stream()
                .map(card -> new SessionFinalizedEvent.RatedCard(card.getId(), card.getPosition(), card.getRating()))
                .toList()
        );

        try {
            MatchResult saved = matchResultRepository.save(
                new MatchResult(UUID.randomUUID(), session, score.homeGoals(), score.awayGoals(), seed, Instant.now())
            );
            publisher.publishEvent(
                new MatchCompletedEvent(
                    UUID.randomUUID(),
                    Instant.now(),
                    sessionId,
                    score.homeGoals(),
                    score.awayGoals(),
                    seed
                )
            );
            return Optional.of(saved);
        } catch (DataIntegrityViolationException duplicate) {
            return matchResultRepository.findBySession_Id(sessionId);
        }
    }
}
