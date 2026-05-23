package dev.sportdraft.feedback.boundary;

import dev.sportdraft.common.web.ClientRequestInfo;
import dev.sportdraft.feedback.control.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/feedback")
public class FeedbackCommandController {

    private final FeedbackService feedbackService;

    public FeedbackCommandController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FeedbackSubmittedResponse submit(
        @Valid @RequestBody SubmitFeedbackRequest request,
        HttpServletRequest httpRequest
    ) {
        String clientIp = ClientRequestInfo.clientIp(httpRequest);
        String userAgent = ClientRequestInfo.userAgent(httpRequest);
        return new FeedbackSubmittedResponse(feedbackService.submit(request, clientIp, userAgent));
    }
}
