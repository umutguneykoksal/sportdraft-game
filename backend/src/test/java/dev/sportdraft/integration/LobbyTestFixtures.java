package dev.sportdraft.integration;

import dev.sportdraft.lobby.control.LobbyService;
import dev.sportdraft.session.entity.GameMode;
import java.util.UUID;

final class LobbyTestFixtures {

    private LobbyTestFixtures() {}

    static UUID openDuelLobby(LobbyService lobbyService, UUID host, String hostName, UUID guest, String guestName) {
        return openLobby(lobbyService, host, hostName, guest, guestName, GameMode.DUEL);
    }

    static UUID openLobby(
        LobbyService lobbyService,
        UUID host,
        String hostName,
        UUID guest,
        String guestName,
        GameMode gameMode
    ) {
        UUID sessionId = lobbyService.createRoom(host, hostName, gameMode).sessionId();
        lobbyService.joinRoom(sessionId, guest, guestName);
        return sessionId;
    }
}
