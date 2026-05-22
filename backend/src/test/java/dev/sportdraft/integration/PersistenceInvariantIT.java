package dev.sportdraft.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.sportdraft.common.events.ProcessedEventRepository;
import dev.sportdraft.competition.entity.CompetitionEntity;
import dev.sportdraft.competition.entity.CompetitionMatchRepository;
import dev.sportdraft.competition.entity.CompetitionRepository;
import dev.sportdraft.competition.control.TournamentBracketService;
import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.redis.ActiveSessionStore;
import dev.sportdraft.simulation.control.DraftCompletedEvent;
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
