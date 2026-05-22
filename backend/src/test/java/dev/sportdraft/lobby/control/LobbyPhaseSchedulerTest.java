package dev.sportdraft.lobby.control;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LobbyPhaseSchedulerTest {

    @Mock
    private LobbyService lobbyService;

    @Test
    void advancePhases_delegatesToLobbyService() {
        LobbyPhaseScheduler scheduler = new LobbyPhaseScheduler(lobbyService);

        scheduler.advancePhases();

        verify(lobbyService).advanceTimedPhases();
    }
}
