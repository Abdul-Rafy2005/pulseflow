package com.pulseflow.backend.analytics;

import com.pulseflow.backend.events.Event;
import com.pulseflow.backend.events.EventRepository;
import com.pulseflow.backend.events.FailedEvent;
import com.pulseflow.backend.events.FailedEventRepository;
import com.pulseflow.backend.events.dto.EventMessage;
import com.pulseflow.backend.queue.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Consumes messages that land in the dead-letter queue after exhausting
 * all retry attempts in {@link AnalyticsConsumer}.
 *
 * For each dead-lettered message this consumer:
 * <ol>
 *   <li>Sets the original event status to {@code FAILED} in Postgres.</li>
 *   <li>Writes a row to the {@code failed_events} table with the failure reason.</li>
 * </ol>
 *
 * See PRD §8 (Failure handling) and §6.3 (failed_events schema).
 */
@Component
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

    private final EventRepository eventRepository;
    private final FailedEventRepository failedEventRepository;
    private final int maxRetryAttempts;

    public DlqConsumer(EventRepository eventRepository,
                       FailedEventRepository failedEventRepository,
                       @Value("${spring.rabbitmq.listener.simple.retry.max-attempts:3}") int maxRetryAttempts) {
        this.eventRepository = eventRepository;
        this.failedEventRepository = failedEventRepository;
        this.maxRetryAttempts = maxRetryAttempts;
    }

    @RabbitListener(queues = RabbitMqConfig.DLQ_NAME)
    @Transactional
    public void processDlqMessage(EventMessage message, @Headers Map<String, Object> headers) {
        log.warn("DLQ received event_id={}", message.eventId());

        // Extract exception info from headers (set by Spring's RepublishMessageRecoverer
        // or by the x-death header from RabbitMQ's native DLX mechanism)
        String reason = extractReason(headers);

        // 1. Mark the event as FAILED in Postgres
        Event event = eventRepository.findById(message.eventId()).orElse(null);
        if (event != null && !"FAILED".equals(event.getStatus())) {
            event.setStatus("FAILED");
            eventRepository.save(event);
        }

        // 2. Write to failed_events table
        FailedEvent failedEvent = new FailedEvent();
        failedEvent.setEventId(message.eventId());
        failedEvent.setReason(reason);
        failedEvent.setRetryCount(maxRetryAttempts);
        failedEventRepository.save(failedEvent);

        log.warn("event_id={} recorded in failed_events — reason: {}",
                message.eventId(), reason.length() > 200 ? reason.substring(0, 200) + "…" : reason);
    }

    /**
     * Attempts to extract a human-readable failure reason from the AMQP headers.
     * Spring's retry interceptor adds {@code x-exception-message} when using
     * RejectAndDontRequeueRecoverer (the default). RabbitMQ native DLX adds
     * {@code x-death} with the original exchange/routing info.
     */
    private String extractReason(Map<String, Object> headers) {
        // Spring retry adds this header
        Object exceptionMessage = headers.get("x-exception-message");
        if (exceptionMessage != null) {
            return exceptionMessage.toString();
        }

        // Fallback: check x-death header from RabbitMQ native DLX
        Object xDeath = headers.get("x-death");
        if (xDeath != null) {
            return "Dead-lettered: " + xDeath.toString();
        }

        return "Unknown failure reason (no exception header found)";
    }
}
