package com.example.auction.boundary.slice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auction.bidding.boundary.AuctionController;
import com.example.auction.bidding.boundary.AuctionCommandController;
import com.example.auction.bidding.control.AuctionEngine;
import com.example.auction.bidding.control.AuctionService;
import com.example.auction.common.realtime.RealtimeConnectionAuthorizer;
import com.example.auction.common.realtime.SessionEventLog;
import com.example.auction.common.sse.SessionEventSyncController;
import com.example.auction.common.web.AuthTokenResolver;
import com.example.auction.common.web.CommandIdempotencyGuard;
import com.example.auction.common.web.GlobalExceptionHandler;
import com.example.auction.lobby.boundary.LobbyController;
import com.example.auction.lobby.boundary.LobbyCommandController;
import com.example.auction.lobby.control.LobbyService;
import com.example.auction.session.control.SessionOrchestrator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class ApiV1V2ParityTest {

    private final AuthTokenResolver authTokenResolver = new AuthTokenResolver();
    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID sessionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID roundId = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private MockMvc v1LobbyMvc;
    private MockMvc v2LobbyMvc;
    private MockMvc syncMvc;
    private MockMvc v1AuctionMvc;
    private MockMvc v2AuctionMvc;

    @BeforeEach
    void setUp() {
        LobbyService lobbyService = mock(LobbyService.class);
        SessionOrchestrator sessionOrchestrator = mock(SessionOrchestrator.class);

        when(lobbyService.joinRoom(eq(sessionId), eq(userId), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Room is full"));
        when(sessionOrchestrator.joinRoom(eq(sessionId), eq(userId), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Room is full"));

        v1LobbyMvc = standalone(new LobbyController(lobbyService, authTokenResolver));
        v2LobbyMvc = standalone(new LobbyCommandController(sessionOrchestrator, authTokenResolver));

        SessionEventLog eventLog = mock(SessionEventLog.class);
        RealtimeConnectionAuthorizer authorizer = mock(RealtimeConnectionAuthorizer.class);
        when(eventLog.since(sessionId, 3)).thenReturn(List.of(Map.of("seq", 4, "eventType", "bid_placed")));
        when(eventLog.currentSeq(sessionId)).thenReturn(4L);
        syncMvc = standalone(new SessionEventSyncController(eventLog, authorizer));

        AuctionService auctionService = mock(AuctionService.class);
        AuctionEngine auctionEngine = mock(AuctionEngine.class);
        when(auctionService.placeBid(eq(sessionId), eq(userId), any(), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Bid is below minimum increment"));
        when(auctionEngine.placeBid(eq(sessionId), eq(userId), any(), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Bid is below minimum increment"));

        v1AuctionMvc = standalone(new AuctionController(auctionService, authTokenResolver));
        v2AuctionMvc = standalone(new AuctionCommandController(
            auctionEngine,
            authTokenResolver,
            new CommandIdempotencyGuard()
        ));
    }

    @Test
    void joinRoom_v1AndV2ReturnSameConflictBody() throws Exception {
        MvcResult v1 = v1LobbyMvc.perform(post("/api/lobbies/{sessionId}/join", sessionId)
                .header("Authorization", "Bearer " + userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"Guest\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Room is full"))
            .andExpect(jsonPath("$.status").value(409))
            .andReturn();

        MvcResult v2 = v2LobbyMvc.perform(post("/api/v2/lobbies/{sessionId}/join", sessionId)
                .header("Authorization", "Bearer " + userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"Guest\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Room is full"))
            .andExpect(jsonPath("$.status").value(409))
            .andReturn();

        assertThat(v1.getResponse().getStatus()).isEqualTo(v2.getResponse().getStatus());
        assertThat(extractDetail(v1)).isEqualTo(extractDetail(v2));
    }

    @Test
    void eventsSync_v1AndV2ReturnSameShape() throws Exception {
        MvcResult v1 = syncMvc.perform(get("/api/sessions/{sessionId}/events/sync", sessionId)
                .param("token", userId.toString())
                .param("afterSeq", "3")
                .param("spectator", "true"))
            .andExpect(status().isOk())
            .andReturn();

        MvcResult v2 = syncMvc.perform(get("/api/v2/sessions/{sessionId}/events/sync", sessionId)
                .param("token", userId.toString())
                .param("afterSeq", "3")
                .param("spectator", "true"))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(v1.getResponse().getContentAsString()).isEqualTo(v2.getResponse().getContentAsString());
    }

    @Test
    void placeBid_v1AndV2ReturnSameValidationError() throws Exception {
        String body = "{\"roundId\":\"" + roundId + "\",\"amountCents\":1}";
        String idempotencyKey = UUID.randomUUID().toString();

        MvcResult v1 = v1AuctionMvc.perform(post("/api/sessions/{sessionId}/bids", sessionId)
                .header("Authorization", "Bearer " + userId)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("Bid is below minimum increment"))
            .andExpect(jsonPath("$.status").value(422))
            .andReturn();

        MvcResult v2 = v2AuctionMvc.perform(post("/api/v2/sessions/{sessionId}/bids", sessionId)
                .header("Authorization", "Bearer " + userId)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("Bid is below minimum increment"))
            .andExpect(jsonPath("$.status").value(422))
            .andReturn();

        assertThat(v1.getResponse().getStatus()).isEqualTo(v2.getResponse().getStatus());
        assertThat(extractDetail(v1)).isEqualTo(extractDetail(v2));
    }

    private MockMvc standalone(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    private static String extractDetail(MvcResult result) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.detail");
    }
}
