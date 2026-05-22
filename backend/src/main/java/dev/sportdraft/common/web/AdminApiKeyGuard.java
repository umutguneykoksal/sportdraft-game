package dev.sportdraft.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Protects admin-only HTTP endpoints. When {@code app.admin.api-key} is unset,
 * admin routes respond with 404 so the surface is hidden in local/dev.
 */
@Component
public class AdminApiKeyGuard {

    public static final String HEADER_NAME = "X-Admin-Key";

    private final String configuredKey;

    public AdminApiKeyGuard(@Value("${app.admin.api-key:}") String apiKey) {
        this.configuredKey = apiKey != null ? apiKey.trim() : "";
    }

    public boolean isEnabled() {
        return !configuredKey.isEmpty();
    }

    public void requireAdmin(String adminKeyHeader) {
        if (!isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        if (adminKeyHeader == null || !configuredKey.equals(adminKeyHeader.trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid admin key");
        }
    }
}
