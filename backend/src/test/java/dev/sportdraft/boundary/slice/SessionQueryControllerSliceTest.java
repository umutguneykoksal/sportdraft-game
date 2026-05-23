package dev.sportdraft.boundary.slice;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sportdraft.common.web.AuthTokenResolver;
import dev.sportdraft.common.web.GlobalExceptionHandler;
import dev.sportdraft.session.boundary.SessionQueryController;
import dev.sportdraft.session.control.SessionReadService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@WebMvcTest(controllers = SessionQueryController.class)
@Import({AuthTokenResolver.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class SessionQueryControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionReadService sessionReadService;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID sessionId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void getMatchResult_notFound_returns404() throws Exception {
        when(sessionReadService.getMatchResult(eq(sessionId), eq(userId)))
            .thenReturn(ResponseEntity.notFound().build());

        mockMvc.perform(get("/api/v2/sessions/{sessionId}/match-result", sessionId)
                .header("Authorization", "Bearer " + userId))
            .andExpect(status().isNotFound());
    }

    @Test
    void getMatchResult_forbidden_returns403() throws Exception {
        UUID outsider = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(sessionReadService.getMatchResult(eq(sessionId), eq(outsider)))
            .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Player not found in session"));

        mockMvc.perform(get("/api/v2/sessions/{sessionId}/match-result", sessionId)
                .header("Authorization", "Bearer " + outsider))
            .andExpect(status().isForbidden());
    }

    @Test
    void getMatchTimeline_notFound_returns404() throws Exception {
        when(sessionReadService.getMatchTimeline(eq(sessionId), eq(userId)))
            .thenReturn(ResponseEntity.notFound().build());

        mockMvc.perform(get("/api/v2/sessions/{sessionId}/match-timeline", sessionId)
                .header("Authorization", "Bearer " + userId))
            .andExpect(status().isNotFound());
    }
}
