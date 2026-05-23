package dev.sportdraft.feedback.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player_feedback")
public class PlayerFeedback {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackCategory category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "page_path", length = 200)
    private String pagePath;

    @Column(name = "app_version", length = 40)
    private String appVersion;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PlayerFeedback() {
    }

    public PlayerFeedback(
        UUID id,
        FeedbackCategory category,
        String message,
        String pagePath,
        String appVersion,
        String userAgent,
        Instant createdAt
    ) {
        this.id = id;
        this.category = category;
        this.message = message;
        this.pagePath = pagePath;
        this.appVersion = appVersion;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public FeedbackCategory getCategory() {
        return category;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
