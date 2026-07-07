package com.pulseflow.backend.events.dto;

import com.pulseflow.backend.common.EventType;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Message published to RabbitMQ after an event is accepted.
 * Must be Serializable so Spring AMQP can convert it.
 * The eventId links back to the persisted Event row so the consumer
 * can update it to PROCESSED/FAILED without re-parsing the payload.
 */
public record EventMessage(
        Long eventId,
        EventType eventType,
        Long userId,
        String source,
        Map<String, Object> metadata,
        LocalDateTime receivedAt
) implements Serializable {
}
