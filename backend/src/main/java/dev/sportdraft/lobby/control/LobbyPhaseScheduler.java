package dev.sportdraft.lobby.control;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LobbyPhaseScheduler {

    private final LobbyService lobbyService;

    public LobbyPhaseScheduler(LobbyService lobbyService) {
        this.lobbyService = lobbyService;
    }

    @Scheduled(fixedDelay = 1000)
    public void advancePhases() {
        lobbyService.advanceTimedPhases();
    }
}
