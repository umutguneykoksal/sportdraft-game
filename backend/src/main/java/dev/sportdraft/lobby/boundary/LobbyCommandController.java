package dev.sportdraft.lobby.boundary;

import dev.sportdraft.common.web.AuthTokenResolver;
import dev.sportdraft.lobby.control.LobbyService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/lobbies")
public class LobbyCommandController {

    private final LobbyService lobbyService;
    private final AuthTokenResolver authTokenResolver;

    public LobbyCommandController(LobbyService lobbyService, AuthTokenResolver authTokenResolver) {
        this.lobbyService = lobbyService;
        this.authTokenResolver = authTokenResolver;
    }

    @GetMapping
    public List<LobbyRoomResponse> listRooms() {
        return lobbyService.listRooms();
    }

    @GetMapping("/pool-options")
    public PoolMinRatingOptionsResponse poolMinRatingOptions(
        @RequestParam(required = false) String poolLeague
    ) {
        return lobbyService.getPoolMinRatingOptions(poolLeague);
    }

    @PostMapping
    public SessionSnapshotResponse createRoom(
        @Valid @RequestBody(required = false) CreateRoomRequest request,
        @RequestHeader("Authorization") String authorization
    ) {
        String nickname = request != null ? request.nickname() : null;
        var gameMode = request != null && request.gameMode() != null
            ? dev.sportdraft.session.entity.GameMode.fromWireOrThrow(request.gameMode())
            : dev.sportdraft.session.entity.GameMode.DUEL;
        return lobbyService.createRoom(
            authTokenResolver.resolveUserId(authorization),
            nickname,
            gameMode,
            request != null ? request.minPoolRating() : null,
            request != null ? request.poolLeague() : null
        );
    }

    @PostMapping("/{sessionId}/join")
    public SessionSnapshotResponse joinRoom(
        @PathVariable UUID sessionId,
        @Valid @RequestBody(required = false) JoinRoomRequest request,
        @RequestHeader("Authorization") String authorization
    ) {
        String nickname = request != null ? request.nickname() : null;
        return lobbyService.joinRoom(
            sessionId,
            authTokenResolver.resolveUserId(authorization),
            nickname
        );
    }
}
