package com.example.auction.lobby.boundary;

import com.example.auction.lobby.control.LobbyService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class LobbyController {

    private final LobbyService lobbyService;

    public LobbyController(LobbyService lobbyService) {
        this.lobbyService = lobbyService;
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
        return lobbyService.createRoom(extractUserId(authorization), nickname);
    }

    @PostMapping("/lobbies/{sessionId}/join")
    public SessionSnapshotResponse joinRoom(
        @PathVariable UUID sessionId,
        @Valid @RequestBody(required = false) JoinLobbyRequest request,
        @RequestHeader("Authorization") String authorization
    ) {
        String nickname = request != null ? request.nickname() : null;
        return lobbyService.joinRoom(sessionId, extractUserId(authorization), nickname);
    }

    @PostMapping("/lobbies/join")
    @Deprecated
    public SessionSnapshotResponse joinLobby(
        @Valid @RequestBody(required = false) JoinLobbyRequest request,
        @RequestHeader("Authorization") String authorization
    ) {
        String nickname = request != null ? request.nickname() : null;
        return lobbyService.joinLobby(extractUserId(authorization), nickname);
    }

    @DeleteMapping("/sessions/{sessionId}/players/me")
    public SessionSnapshotResponse leaveLobby(
        @PathVariable UUID sessionId,
        @RequestHeader("Authorization") String authorization
    ) {
        return lobbyService.leaveLobby(sessionId, extractUserId(authorization));
    }

    private UUID extractUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Authorization: Bearer <uuid>");
        }
        try {
            return UUID.fromString(authorization.substring("Bearer ".length()).trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bearer token format");
        }
    }
}
