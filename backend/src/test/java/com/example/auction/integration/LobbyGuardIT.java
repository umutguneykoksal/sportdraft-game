package com.example.auction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auction.lobby.control.LobbyService;
import com.example.auction.session.entity.Formation;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class LobbyGuardIT extends AbstractIntegrationTest {

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private ActiveSessionStore sessionStore;

    @Test
    void createRoom_rejectsWhenMaxConcurrentRoomsReached() {
        for (int i = 0; i < 4; i++) {
            lobbyService.createRoom(UUID.randomUUID(), "Host-" + i, GameMode.DUEL);
        }

        assertThatThrownBy(() -> lobbyService.createRoom(UUID.randomUUID(), "Host-5", GameMode.DUEL))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).contains("Maximum 4 concurrent rooms");
            });
    }

    @Test
    void joinRoom_rejectsWhenRoomFull() {
        UUID host = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID extra = UUID.randomUUID();

        UUID sessionId = lobbyService.createRoom(host, "Host", GameMode.DUEL).sessionId();
        lobbyService.joinRoom(sessionId, guest, "Guest");

        assertThatThrownBy(() -> lobbyService.joinRoom(sessionId, extra, "Extra"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void joinRoom_rejectsWhenRoomClosed() {
        UUID host = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID extra = UUID.randomUUID();

        UUID sessionId = lobbyService.createRoom(host, "Host", GameMode.DUEL).sessionId();
        lobbyService.joinRoom(sessionId, guest, "Guest");

        ActiveSession session = sessionStore.getById(sessionId);
        assertThat(session.getStatus()).isNotEqualTo(com.example.auction.session.entity.SessionStatus.WAITING);

        assertThatThrownBy(() -> lobbyService.joinRoom(sessionId, extra, "LateJoiner"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("Room is no longer accepting players");
            });
    }

    @Test
    void selectFormation_rejectsOutsideFormationPhase() {
        UUID host = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID sessionId = lobbyService.createRoom(host, "Host", GameMode.DUEL).sessionId();
        lobbyService.joinRoom(sessionId, guest, "Guest");
        CompetitionTestFixtures.advanceToAuctioning(lobbyService, sessionStore, sessionId, host, guest);

        assertThatThrownBy(() -> lobbyService.selectFormation(sessionId, host, Formation.FOUR_THREE_THREE))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("Session is not in formation selection phase");
            });
    }

    @Test
    void confirmFormationReady_rejectsOutsidePhase() {
        UUID host = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID sessionId = lobbyService.createRoom(host, "Host", GameMode.DUEL).sessionId();
        lobbyService.joinRoom(sessionId, guest, "Guest");
        CompetitionTestFixtures.advanceToAuctioning(lobbyService, sessionStore, sessionId, host, guest);

        assertThatThrownBy(() -> lobbyService.confirmFormationReady(sessionId, host))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).isEqualTo("Not in formation selection phase");
            });
    }
}
