package com.example.auction.session.control;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionTickProcessor {

    private final SessionOrchestrator sessionOrchestrator;

    public SessionTickProcessor(SessionOrchestrator sessionOrchestrator) {
        this.sessionOrchestrator = sessionOrchestrator;
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        sessionOrchestrator.runTimedTransitions();
    }
}
