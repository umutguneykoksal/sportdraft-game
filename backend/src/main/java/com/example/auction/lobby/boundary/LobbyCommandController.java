package com.example.auction.lobby.boundary;

import com.example.auction.common.web.AuthTokenResolver;
import com.example.auction.session.control.SessionOrchestrator;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/lobbies")
public class LobbyCommandController {

    private final SessionOrchestrator sessionOrchestrator;
    private final AuthTokenResolver authTokenResolver;

    public LobbyCommandController(SessionOrchestrator sessionOrchestrator, AuthTokenResolver authTokenResolver) {
        this.sessionOrchestrator = sessionOrchestrator;
        this.authTokenResolver = authTokenResolver;
    }

    @GetMapping
    public List<LobbyRoomResponse> listRooms() {
        return sessionOrchestrator.listRooms();
    }

    @PostMapping
    public SessionSnapshotResponse createRoom(
        @Valid @RequestBody(required = false) JoinLobbyRequest request,
        @RequestHeader("Authorization") String authorization
    ) {
        String nickname = request != null ? request.nickname() : null;
        return sessionOrchestrator.createRoom(authTokenResolver.resolveUserId(authorization), nickname);
    }

    /**
     * @deprecated Prefer {@link #createRoom} + {@link #joinRoom}. Kept for legacy clients.
     */
    @Deprecated
    @PostMapping("/join")
    public SessionSnapshotResponse joinLobby(
        @Valid @RequestBody(required = false) JoinLobbyRequest request,
        @RequestHeader("Authorization") String authorization
    ) {
        String nickname = request != null ? request.nickname() : null;
        return sessionOrchestrator.joinLobby(authTokenResolver.resolveUserId(authorization), nickname);
    }

    @PostMapping("/{sessionId}/join")
    public SessionSnapshotResponse joinRoom(
        @PathVariable UUID sessionId,
        @Valid @RequestBody(required = false) JoinLobbyRequest request,
        @RequestHeader("Authorization") String authorization
    ) {
        String nickname = request != null ? request.nickname() : null;
        return sessionOrchestrator.joinRoom(
            sessionId,
            authTokenResolver.resolveUserId(authorization),
            nickname
        );
    }
}
