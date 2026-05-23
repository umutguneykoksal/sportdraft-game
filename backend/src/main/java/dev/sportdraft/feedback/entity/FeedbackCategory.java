package dev.sportdraft.feedback.entity;

public enum FeedbackCategory {
    BUG,
    IDEA,
    OTHER;

    public static FeedbackCategory fromWire(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("category is required");
        }
        return FeedbackCategory.valueOf(value.trim().toUpperCase());
    }
}
