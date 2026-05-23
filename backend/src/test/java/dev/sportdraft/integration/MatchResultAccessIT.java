package dev.sportdraft.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sportdraft.lobby.control.LobbyService;
import dev.sportdraft.session.control.SessionReadService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class MatchResultAccessIT extends AbstractIntegrationTest {

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private SessionReadService sessionReadService;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void getMatchResult_rejectsNonMemberViaService() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();

        UUID sessionId = LobbyTestFixtures.openDuelLobby(lobbyService, user1, "Home", user2, "Away");

        assertThatThrownBy(() -> sessionReadService.getMatchResult(sessionId, outsider))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(rse.getReason()).isEqualTo("Player not found in session");
            });
    }

    @Test
    void getMatchResult_rejectsNonMemberViaHttp() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();

        UUID sessionId = LobbyTestFixtures.openDuelLobby(lobbyService, user1, "Home", user2, "Away");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(outsider.toString());
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "http://localhost:" + port + "/api/v2/sessions/" + sessionId + "/match-result",
            HttpMethod.GET,
            request,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Player not found in session");
    }
}
