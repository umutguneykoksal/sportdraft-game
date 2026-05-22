package dev.sportdraft.session.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sportdraft.lobby.boundary.SessionSnapshotResponse;
import dev.sportdraft.lobby.control.LobbyService;
import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.entity.PoolLeague;
import dev.sportdraft.session.entity.SessionStatus;
import java.util.List;
import dev.sportdraft.bidding.control.AuctionService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionOrchestratorTest {

    @Mock
    private LobbyService lobbyService;
    @Mock
    private AuctionService auctionService;
    @InjectMocks
    private SessionOrchestrator sessionOrchestrator;

    @Test
    void createRoomDelegatesToLobbyService() {
        UUID userId = UUID.randomUUID();
        SessionSnapshotResponse snap = new SessionSnapshotResponse(
            UUID.randomUUID(), SessionStatus.WAITING, Instant.now(), 1, true,
            Formation.FOUR_FOUR_TWO, null, 0, false, 0L, 0L,
            GameMode.DUEL, 2, 70, PoolLeague.PREMIER_LEAGUE, List.of()
        );
        when(lobbyService.createRoom(userId, "nick")).thenReturn(snap);

        assertThat(sessionOrchestrator.createRoom(userId, "nick")).isEqualTo(snap);
        verify(lobbyService).createRoom(userId, "nick");
    }

    @Test
    void confirmMatchStartDelegatesAndReturnsSnapshot() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SessionSnapshotResponse snap = new SessionSnapshotResponse(
            sessionId,
            SessionStatus.MATCH_START_PENDING,
            Instant.now(),
            2,
            true,
            Formation.FOUR_FOUR_TWO,
            Instant.now(),
            1,
            true,
            1L,
            0L,
            GameMode.DUEL,
            2,
            70,
            PoolLeague.PREMIER_LEAGUE,
            List.of()
        );
        when(lobbyService.getSnapshot(sessionId, userId)).thenReturn(snap);

        SessionSnapshotResponse response = sessionOrchestrator.confirmMatchStart(sessionId, userId);

        verify(auctionService).confirmMatchStart(sessionId, userId);
        assertThat(response).isEqualTo(snap);
    }
}
