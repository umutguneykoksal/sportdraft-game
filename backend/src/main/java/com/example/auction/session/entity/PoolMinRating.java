package com.example.auction.session.entity;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Allowed minimum overall ratings for the auction card pool (room host picks at create). */
public final class PoolMinRating {

    public static final int DEFAULT = 70;
    public static final List<Integer> ALLOWED = List.of(70, 75, 80);

    private PoolMinRating() {}

    public static int resolve(Integer requested) {
        if (requested == null) {
            return DEFAULT;
        }
        if (!ALLOWED.contains(requested)) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "minPoolRating must be one of: " + ALLOWED
            );
        }
        return requested;
    }

    /** Legacy Redis sessions may lack the field (0). */
    public static int effective(int stored) {
        return stored <= 0 ? DEFAULT : stored;
    }
}
