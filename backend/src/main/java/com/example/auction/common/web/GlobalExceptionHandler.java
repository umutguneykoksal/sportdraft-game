package com.example.auction.common.web;

import com.example.auction.session.redis.ActiveSessionStore;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
            .body(errorBody(ex.getStatusCode().value(), ex.getStatusCode().toString(), ex.getReason()));
    }

    @ExceptionHandler(ActiveSessionStore.OptimisticLockException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(ActiveSessionStore.OptimisticLockException ex) {
        log.debug("OptimisticLockException: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(errorBody(409, "CONFLICT", "Concurrent update — please retry"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Validation failed";
        }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(errorBody(400, "BAD_REQUEST", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorBody(500, "INTERNAL_SERVER_ERROR", "An unexpected error occurred"));
    }

    private static Map<String, Object> errorBody(int status, String error, String message) {
        return Map.of(
            "status", status,
            "error", error,
            "message", message != null ? message : "Unknown error",
            "timestamp", Instant.now().toString()
        );
    }
}
