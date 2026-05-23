package dev.sportdraft.feedback.boundary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubmitFeedbackRequest(
    @NotBlank
    @Pattern(regexp = "BUG|IDEA|OTHER", message = "must be BUG, IDEA, or OTHER")
    String category,

    @NotBlank
    @Size(min = 10, max = 2000)
    String message,

    @Size(max = 200)
    String pagePath,

    @Size(max = 40)
    String appVersion
) {
}
