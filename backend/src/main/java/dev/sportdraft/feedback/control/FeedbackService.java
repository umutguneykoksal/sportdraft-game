package dev.sportdraft.feedback.control;

import dev.sportdraft.feedback.boundary.SubmitFeedbackRequest;
import dev.sportdraft.feedback.entity.FeedbackCategory;
import dev.sportdraft.feedback.entity.PlayerFeedback;
import dev.sportdraft.feedback.entity.PlayerFeedbackRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {

    private final PlayerFeedbackRepository repository;
    private final FeedbackRateLimiter rateLimiter;

    public FeedbackService(PlayerFeedbackRepository repository, FeedbackRateLimiter rateLimiter) {
        this.repository = repository;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public UUID submit(SubmitFeedbackRequest request, String clientIp, String userAgent) {
        rateLimiter.checkAllowed(clientIp);

        FeedbackCategory category = FeedbackCategory.fromWire(request.category());
        UUID id = UUID.randomUUID();
        PlayerFeedback feedback = new PlayerFeedback(
            id,
            category,
            request.message().trim(),
            normalizeOptional(request.pagePath()),
            normalizeOptional(request.appVersion()),
            userAgent,
            Instant.now()
        );
        repository.save(feedback);
        return id;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
