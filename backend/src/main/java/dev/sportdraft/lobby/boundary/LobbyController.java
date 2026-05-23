package dev.sportdraft.lobby.boundary;

import dev.sportdraft.common.web.AuthTokenResolver;
import dev.sportdraft.lobby.control.LobbyService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @deprecated Use {@code /api/v2/lobbies} and {@code /api/v2/sessions}. Retained as operational fallback.
 */
@Deprecated
@RestController
@RequestMapping("/api")
public class LobbyController {

    private final LobbyService lobbyService;
    private final AuthTokenResolver authTokenResolver;

    public LobbyController(LobbyService lobbyService, AuthTokenResolver authTokenResolver) {
        this.lobbyService = lobbyService;
        this.authTokenResolver = authTokenResolver;
    }

    @GetMapping("/lobbies")
    public List<LobbyRoomResponse> listRooms() {
        return lobbyService.listRooms();
    }

    @PostMapping("/lobbies")
    public SessionSnapshotResponse createRoom(
        @Valid @RequestBody(required = false) JoinLobbyRequest request,
        @RequestHeader("Authorization") String authorization
    ) {
        String nickname = request != null ? request.nickname() : null;
        return lobbyService.createRoom(authTokenResolver.resolveUserId(authorization), nickname);
    }

    @PostMapping("/lobbies/{sessionId}/join")
    public SessionSnapshotResponse joinRoom(
        @PathVariable UUID sessionId,
        @Valid @RequestBody(required = false) JoinLobbyRequest request,
        @RequestHeader("Authorization") String authorization
    ) {
        String nickname = request != null ? request.nickname() : null;
        return lobbyService.joinRoom(sessionId, authTokenResolver.resolveUserId(authorization), nickname);
    }

    @DeleteMapping("/sessions/{sessionId}/players/me")
    public SessionSnapshotResponse leaveLobby(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        return lobbyService.leaveLobby(sessionId, authTokenResolver.resolveUserId(authorization));
    }
}
