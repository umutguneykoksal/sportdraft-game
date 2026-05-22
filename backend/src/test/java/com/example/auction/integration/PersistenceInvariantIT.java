package com.example.auction.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.auction.common.events.ProcessedEventRepository;
import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchRepository;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.competition.control.TournamentBracketService;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.redis.ActiveSessionStore;
import com.example.auction.simulation.control.DraftCompletedEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

class PersistenceInvariantIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private CompetitionRepository competitionRepository;

    @Autowired
    private CompetitionMatchRepository matchRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private TournamentBracketService tournamentBracketService;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Test
    void draftCompletedEvent_isIdempotent() {
        UUID sessionId = UUID.randomUUID();
        UUID eventId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        seedSession(sessionId, GameMode.TOURNAMENT);

        DraftCompletedEvent event = CompetitionTestFixtures.tournamentEventWithId(sessionId, eventId);
        eventPublisher.publishEvent(event);
        eventPublisher.publishEvent(event);

        await().atMost(10, SECONDS).until(() -> competitionRepository.findBySessionId(sessionId).isPresent());

        assertThat(competitionRepository.findBySessionId(sessionId)).isPresent();
        assertThat(competitionRepository.findAll()).hasSize(1);
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }

    @Test
    void competitionSessionId_isUnique() {
        UUID sessionId = UUID.randomUUID();
        seedSession(sessionId, GameMode.TOURNAMENT);

        DraftCompletedEvent first = CompetitionTestFixtures.tournamentEvent(sessionId);
        eventPublisher.publishEvent(first);

        await().atMost(10, SECONDS).until(() -> competitionRepository.findBySessionId(sessionId).isPresent());

        DraftCompletedEvent second = CompetitionTestFixtures.tournamentEvent(sessionId);
        eventPublisher.publishEvent(second);

        assertThat(competitionRepository.findBySessionId(sessionId)).isPresent();
        assertThat(competitionRepository.findAll()).hasSize(1);
        assertThat(processedEventRepository.count()).isEqualTo(2);
    }

    @Test
    void matchBelongsToCompetition() {
        UUID sessionId = UUID.randomUUID();
        seedSession(sessionId, GameMode.TOURNAMENT);

        CompetitionEntity competition = tournamentBracketService.initializeTournament(
            CompetitionTestFixtures.tournamentEvent(sessionId)
        );

        assertThat(matchRepository.findByCompetitionIdOrderBySortOrderAsc(competition.getId())).hasSize(2);
        assertThat(matchRepository.findByCompetitionIdOrderBySortOrderAsc(competition.getId()))
            .allMatch(match -> competition.getId().equals(match.getCompetitionId()));
    }

    private void seedSession(UUID sessionId, GameMode gameMode) {
        sessionStore.save(CompetitionTestFixtures.activeSession(sessionId, gameMode));
    }
}
