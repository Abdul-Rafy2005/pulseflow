package com.pulseflow.backend.analytics;

import com.pulseflow.backend.dashboard.EventBroadcastService;
import com.pulseflow.backend.events.Event;
import com.pulseflow.backend.events.EventRepository;
import com.pulseflow.backend.events.dto.EventMessage;
import com.pulseflow.backend.queue.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Consumes events from the main queue, updates Postgres status to PROCESSED,
 * and pushes hot counters to Redis.
 *
 * Retry policy is configured in application.yaml (Spring AMQP retry interceptor).
 * After exhausting retries the message is nacked and routed to the DLQ via the
 * queue's dead-letter-exchange argument — the {@link DlqConsumer} picks it up
 * from there.
 */
@Component
public class AnalyticsConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsConsumer.class);

    private final EventRepository eventRepository;
    private final AnalyticsRedisService redisService;
    private final EventBroadcastService broadcastService;

    public AnalyticsConsumer(EventRepository eventRepository,
                             AnalyticsRedisService redisService,
                             EventBroadcastService broadcastService) {
        this.eventRepository = eventRepository;
        this.redisService = redisService;
        this.broadcastService = broadcastService;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_NAME)
    @Transactional
    public void processEvent(EventMessage message) {
        log.info("Processing event_id={}", message.eventId());

        // 1. Fetch from DB
        Event event = eventRepository.findById(message.eventId()).orElse(null);
        if (event == null) {
            log.warn("Event {} not found in database. Throwing exception to trigger retry.", message.eventId());
            throw new RuntimeException("Event " + message.eventId() + " not found in database");
        }

        // Idempotency check — don't reprocess if already done
        if ("PROCESSED".equals(event.getStatus())) {
            log.info("Event {} already processed. Skipping.", message.eventId());
            return;
        }

        // Deliberate failure for testing DLQ/Retries (Phase 5)
        if ("poison-pill".equals(message.source())) {
            log.error("Poison pill detected for event_id={}, throwing exception...", message.eventId());
            throw new RuntimeException("Simulated failure for DLQ testing");
        }

        // 2. Update Redis Counters first (if this throws, the message retries
        //    and the DB is still PENDING — safe to retry)
        redisService.recordEvent(message);

        // 3. Update Postgres — mark as PROCESSED only after Redis succeeds
        event.setStatus("PROCESSED");
        event.setProcessedAt(LocalDateTime.now());
        eventRepository.save(event);

        // 4. Broadcast to WebSocket subscribers
        try {
            long totalEventsToday = redisService.getTotalEventsToday();
            long activeUsersToday = redisService.getActiveUsersToday();
            broadcastService.broadcastEvent(message, totalEventsToday, activeUsersToday);
        } catch (Exception e) {
            // Broadcasting is best-effort — never fail the consumer for a WS push failure
            log.warn("Failed to broadcast event_id={} via WebSocket: {}", message.eventId(), e.getMessage());
        }

        log.info("Finished processing event_id={}", message.eventId());
    }
}
