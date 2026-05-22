package com.example.auction.boundary.slice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auction.common.web.AuthTokenResolver;
import com.example.auction.common.web.GlobalExceptionHandler;
import com.example.auction.lobby.boundary.LobbyCommandController;
import com.example.auction.session.control.SessionOrchestrator;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.PoolLeague;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(controllers = LobbyCommandController.class)
@Import({AuthTokenResolver.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class LobbyCommandControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionOrchestrator sessionOrchestrator;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID sessionId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void joinRoom_roomFull_returns409() throws Exception {
        when(sessionOrchestrator.joinRoom(eq(sessionId), eq(userId), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Room is full"));

        mockMvc.perform(post("/api/v2/lobbies/{sessionId}/join", sessionId)
                .header("Authorization", "Bearer " + userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"Guest\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Room is full"));
    }

    @Test
    void createRoom_poolTooSmall_returns422() throws Exception {
        when(sessionOrchestrator.createRoom(
            eq(userId),
            eq("Host"),
            eq(GameMode.DUEL),
            eq(80),
            eq(PoolLeague.SUPER_LIG.name())
        )).thenThrow(new ResponseStatusException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Not enough cards for SUPER_LIG at minimum rating 80 (30 available, need 44)"
        ));

        mockMvc.perform(post("/api/v2/lobbies")
                .header("Authorization", "Bearer " + userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "nickname": "Host",
                      "gameMode": "DUEL",
                      "minPoolRating": 80,
                      "poolLeague": "SUPER_LIG"
                    }
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("Not enough cards for SUPER_LIG at minimum rating 80 (30 available, need 44)"));
    }
}
