package dev.sportdraft.common.web;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuthTokenResolver {

    public UUID resolveUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Authorization: Bearer <uuid>");
        }
        try {
            return UUID.fromString(authorization.substring("Bearer ".length()).trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bearer token format");
        }
    }
}
