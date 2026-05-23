package dev.sportdraft.feedback.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sportdraft.feedback.boundary.SubmitFeedbackRequest;
import dev.sportdraft.feedback.entity.FeedbackCategory;
import dev.sportdraft.feedback.entity.PlayerFeedback;
import dev.sportdraft.feedback.entity.PlayerFeedbackRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private PlayerFeedbackRepository repository;

    @Mock
    private FeedbackRateLimiter rateLimiter;

    @InjectMocks
    private FeedbackService feedbackService;

    @Test
    void submit_persistsAnonymousFeedback() {
        when(repository.save(any(PlayerFeedback.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new SubmitFeedbackRequest(
            "BUG",
            "  Timer shows 13 instead of 15 seconds.  ",
            "/lobby",
            "0.1.0"
        );

        var id = feedbackService.submit(request, "10.0.0.1", "TestAgent/1.0");

        assertThat(id).isNotNull();
        verify(rateLimiter).checkAllowed("10.0.0.1");

        ArgumentCaptor<PlayerFeedback> captor = ArgumentCaptor.forClass(PlayerFeedback.class);
        verify(repository).save(captor.capture());
        PlayerFeedback saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(id);
        assertThat(saved.getCategory()).isEqualTo(FeedbackCategory.BUG);
        assertThat(saved.getMessage()).isEqualTo("Timer shows 13 instead of 15 seconds.");
    }

    @Test
    void submit_propagatesRateLimit() {
        var request = new SubmitFeedbackRequest("IDEA", "Add more leagues please!", null, null);
        var rateLimit = new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many feedback submissions — try again later");
        org.mockito.Mockito.doThrow(rateLimit).when(rateLimiter).checkAllowed("10.0.0.1");

        assertThatThrownBy(() -> feedbackService.submit(request, "10.0.0.1", null))
            .isSameAs(rateLimit);
    }
}
