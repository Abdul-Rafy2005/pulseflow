package com.pulseflow.backend.dashboard;

import com.pulseflow.backend.events.dto.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Broadcasts processed events and updated stats to WebSocket subscribers.
 *
 * Called by {@link com.pulseflow.backend.analytics.AnalyticsConsumer} after
 * an event is successfully processed. Pushes to:
 * <ul>
 *   <li>{@code /topic/events} — the processed event details</li>
 *   <li>{@code /topic/stats} — a lightweight stats snapshot</li>
 * </ul>
 */
@Service
public class EventBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(EventBroadcastService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public EventBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Broadcasts a processed event to all subscribed WebSocket clients.
     *
     * @param message  the original event message from the queue
     * @param totalEventsToday current total events today (from Redis counter)
     * @param activeUsersToday current active users today (from Redis set size)
     */
    public void broadcastEvent(EventMessage message, long totalEventsToday, long activeUsersToday) {
        log.info("Broadcasting event_id={} to WebSocket subscribers", message.eventId());

        // 1. Push the individual event to /topic/events
        EventBroadcast eventPayload = new EventBroadcast(
                message.eventId(),
                message.eventType().name(),
                message.userId(),
                message.source(),
                message.metadata(),
                LocalDateTime.now()
        );
        messagingTemplate.convertAndSend("/topic/events", eventPayload);

        // 2. Push updated stats snapshot to /topic/stats
        StatsBroadcast statsPayload = new StatsBroadcast(
                totalEventsToday,
                activeUsersToday,
                LocalDateTime.now()
        );
        messagingTemplate.convertAndSend("/topic/stats", statsPayload);
    }

    /**
     * Payload sent to {@code /topic/events} subscribers.
     */
    public record EventBroadcast(
            Long eventId,
            String eventType,
            Long userId,
            String source,
            Map<String, Object> metadata,
            LocalDateTime processedAt
    ) {}

    /**
     * Payload sent to {@code /topic/stats} subscribers.
     */
    public record StatsBroadcast(
            long totalEventsToday,
            long activeUsersToday,
            LocalDateTime timestamp
    ) {}
}
