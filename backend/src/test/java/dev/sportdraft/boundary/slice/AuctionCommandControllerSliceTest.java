package dev.sportdraft.boundary.slice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sportdraft.bidding.boundary.AuctionCommandController;
import dev.sportdraft.bidding.control.AuctionEngine;
import dev.sportdraft.common.web.AuthTokenResolver;
import dev.sportdraft.common.web.CommandIdempotencyGuard;
import dev.sportdraft.common.web.GlobalExceptionHandler;
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

@WebMvcTest(controllers = AuctionCommandController.class)
@Import({AuthTokenResolver.class, CommandIdempotencyGuard.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class AuctionCommandControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuctionEngine auctionEngine;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID sessionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID roundId = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void placeBid_validationError_returns422() throws Exception {
        when(auctionEngine.placeBid(eq(sessionId), eq(userId), any(), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Bid is below minimum increment"));

        mockMvc.perform(post("/api/v2/sessions/{sessionId}/bids", sessionId)
                .header("Authorization", "Bearer " + userId)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roundId\":\"" + roundId + "\",\"amountCents\":1}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("Bid is below minimum increment"));
    }

    @Test
    void placeBid_invalidIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/api/v2/sessions/{sessionId}/bids", sessionId)
                .header("Authorization", "Bearer " + userId)
                .header("Idempotency-Key", "not-a-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roundId\":\"" + roundId + "\",\"amountCents\":1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Invalid Idempotency-Key value"));
    }
}
