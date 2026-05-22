package dev.sportdraft.common.web;

import dev.sportdraft.session.redis.ActiveSessionStore;
import java.net.URI;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final URI ABOUT_BLANK = URI.create("about:blank");

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException ex) {
        log.debug("ResponseStatusException: {} {}", ex.getStatusCode(), ex.getReason());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), detailMessage(ex.getReason()));
        problem.setTitle(ex.getStatusCode().toString());
        problem.setType(ABOUT_BLANK);
        return ResponseEntity.status(ex.getStatusCode()).body(problem);
    }

    @ExceptionHandler(ActiveSessionStore.OptimisticLockException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(ActiveSessionStore.OptimisticLockException ex) {
        log.debug("OptimisticLockException: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, "Concurrent update — please retry"
        );
        problem.setTitle("Conflict");
        problem.setType(ABOUT_BLANK);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Validation failed";
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        problem.setTitle("Bad Request");
        problem.setType(ABOUT_BLANK);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"
        );
        problem.setTitle("Internal Server Error");
        problem.setType(ABOUT_BLANK);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private static String detailMessage(String reason) {
        return reason != null ? reason : "Unknown error";
    }
}
