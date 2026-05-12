package com.example.auction.common.web;

import com.example.auction.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        log.debug("ResponseStatusException: {} {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity
            .status(ex.getStatusCode())
            .body(Map.of(
                "status", ex.getStatusCode().value(),
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason() != null ? ex.getReason() : "Unknown error",
                "timestamp", Instant.now().toString()
            ));
    }

    @ExceptionHandler(ActiveSessionStore.OptimisticLockException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(ActiveSessionStore.OptimisticLockException ex) {
        log.debug("OptimisticLockException: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(Map.of(
                "status", 409,
                "error", "CONFLICT",
                "message", "Concurrent update — please retry",
                "timestamp", Instant.now().toString()
            ));
    }
}
