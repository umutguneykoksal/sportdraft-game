package com.example.auction.session.control;

import com.example.auction.session.entity.SessionStatus;
import org.springframework.stereotype.Component;

@Component
public class SessionPhasePolicy {

    public boolean canMutateAuction(SessionStatus status) {
        return status == SessionStatus.AUCTIONING || status == SessionStatus.MATCH_START_PENDING;
    }
}
