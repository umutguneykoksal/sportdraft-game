package dev.sportdraft.session.control;

import dev.sportdraft.session.entity.SessionStatus;
import org.springframework.stereotype.Component;

@Component
public class SessionPhasePolicy {

    public boolean canMutateAuction(SessionStatus status) {
        return status == SessionStatus.AUCTIONING || status == SessionStatus.MATCH_START_PENDING;
    }
}
