package dev.sportdraft.boundary.slice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sportdraft.common.web.GlobalExceptionHandler;
import dev.sportdraft.feedback.boundary.FeedbackCommandController;
import dev.sportdraft.feedback.control.FeedbackService;
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

@WebMvcTest(controllers = FeedbackCommandController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class FeedbackCommandControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FeedbackService feedbackService;

    private final UUID feedbackId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void submit_validRequest_returns202() throws Exception {
        when(feedbackService.submit(any(), eq("127.0.0.1"), any())).thenReturn(feedbackId);

        mockMvc.perform(post("/api/v2/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "category": "BUG",
                      "message": "Auction timer shows the wrong countdown value.",
                      "pagePath": "/lobby"
                    }
                    """)
                .requestAttr("javax.servlet.request.remote_addr", "127.0.0.1"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.id").value(feedbackId.toString()));
    }

    @Test
    void submit_messageTooShort_returns400() throws Exception {
        mockMvc.perform(post("/api/v2/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "category": "IDEA",
                      "message": "short"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void submit_rateLimited_returns429() throws Exception {
        when(feedbackService.submit(any(), any(), any()))
            .thenThrow(new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many feedback submissions — try again later"
            ));

        mockMvc.perform(post("/api/v2/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "category": "OTHER",
                      "message": "Great game overall, thanks for building this!"
                    }
                    """))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.detail").value("Too many feedback submissions — try again later"));
    }
}
