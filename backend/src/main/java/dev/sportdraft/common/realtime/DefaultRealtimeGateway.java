package dev.sportdraft.common.realtime;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Publishes events via SessionRealtimeHub with ordered seq numbers from SessionEventLog.
 *
 * Ordering guarantee: events are appended to the log BEFORE being pushed to clients.
 * This ensures a reconnecting client who fetches /events/sync?afterSeq=N will always
 * find the event in the log, even if it arrived on push just before the reconnect.
 *
 * Heartbeat events are excluded from the event log (no seq) to avoid polluting the ring buffer.
 */
@Component
public class DefaultRealtimeGateway implements RealtimeGateway {

    private static final Logger log = LoggerFactory.getLogger(DefaultRealtimeGateway.class);
    private static final String HEARTBEAT_EVENT = "heartbeat";

    private final SessionRealtimeHub sessionRealtimeHub;
    private final RealtimeEnvelopeFactory envelopeFactory;
    private final SessionEventLog eventLog;

    public DefaultRealtimeGateway(
        SessionRealtimeHub sessionRealtimeHub,
        RealtimeEnvelopeFactory envelopeFactory,
        SessionEventLog eventLog
    ) {
        this.sessionRealtimeHub = sessionRealtimeHub;
        this.envelopeFactory = envelopeFactory;
        this.eventLog = eventLog;
    }

    @Override
    public void publish(UUID sessionId, String eventType, Object payload) {
        if (HEARTBEAT_EVENT.equals(eventType)) {
            // Heartbeats keep connections alive but are not meaningful state changes;
            // skip seq and log to avoid ring buffer noise.
            Map<String, Object> envelope = envelopeFactory.create(sessionId, eventType, payload, 0L);
            sessionRealtimeHub.publish(sessionId, envelope);
            return;
        }
        long seq = eventLog.nextSeq(sessionId);
        Map<String, Object> envelope = envelopeFactory.create(sessionId, eventType, payload, seq);
        // Append BEFORE publish: reconnecting client calling /sync?afterSeq will always find the event.
        if (!eventLog.append(sessionId, envelope)) {
            log.error("Event log append failed for session={} eventType={} seq={} — skipping live publish",
                sessionId, eventType, seq);
            return;
        }
        sessionRealtimeHub.publish(sessionId, envelope);
    }
}
