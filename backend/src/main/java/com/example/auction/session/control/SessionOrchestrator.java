package com.example.auction.session.control;

import com.example.auction.bidding.control.AuctionService;
import com.example.auction.lobby.boundary.SessionSnapshotResponse;
import com.example.auction.lobby.control.LobbyService;
import com.example.auction.session.entity.Formation;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SessionOrchestrator {

    private final LobbyService lobbyService;
    private final AuctionService auctionService;

    public SessionOrchestrator(LobbyService lobbyService, AuctionService auctionService) {
        this.lobbyService = lobbyService;
        this.auctionService = auctionService;
    }

    public SessionSnapshotResponse joinLobby(UUID userId, String nickname) {
        return lobbyService.joinLobby(userId, nickname);
    }

    public SessionSnapshotResponse leaveLobby(UUID sessionId, UUID userId) {
        return lobbyService.leaveLobby(sessionId, userId);
    }

    public SessionSnapshotResponse getSnapshot(UUID sessionId, UUID userId) {
        return lobbyService.getSnapshot(sessionId, userId);
    }

    public SessionSnapshotResponse selectFormation(UUID sessionId, UUID userId, Formation formation) {
        return lobbyService.selectFormation(sessionId, userId, formation);
    }

    public SessionSnapshotResponse confirmMatchStart(UUID sessionId, UUID userId) {
        auctionService.confirmMatchStart(sessionId, userId);
        return lobbyService.getSnapshot(sessionId, userId);
    }

    public void runTimedTransitions() {
        lobbyService.advanceTimedPhases();
    }
}
