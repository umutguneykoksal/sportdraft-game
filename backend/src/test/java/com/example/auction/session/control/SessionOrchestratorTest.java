package com.example.auction.session.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auction.lobby.boundary.SessionSnapshotResponse;
import com.example.auction.lobby.control.LobbyService;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.SessionStatus;
import java.util.List;
import com.example.auction.bidding.control.AuctionService;
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
            GameMode.DUEL, 2, List.of()
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
            List.of()
        );
        when(lobbyService.getSnapshot(sessionId, userId)).thenReturn(snap);

        SessionSnapshotResponse response = sessionOrchestrator.confirmMatchStart(sessionId, userId);

        verify(auctionService).confirmMatchStart(sessionId, userId);
        assertThat(response).isEqualTo(snap);
    }
}
