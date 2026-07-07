package com.pulseflow.backend.events;

import com.pulseflow.backend.common.EventType;
import com.pulseflow.backend.events.dto.CreateEventRequest;
import com.pulseflow.backend.events.dto.EventAcceptedResponse;
import com.pulseflow.backend.events.dto.EventMessage;
import com.pulseflow.backend.events.dto.EventResponse;
import com.pulseflow.backend.queue.RabbitMqConfig;
import java.time.LocalDateTime;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Write-path service for Phase 3.
 *
 * Contract (per PRD §4 and CLAUDE.md §6):
 *   1. Persist the Event row with status=PENDING  (so we have a durable record
 *      before touching the queue — the ID goes into the message).
 *   2. Publish an EventMessage to RabbitMQ.
 *   3. Return 202 immediately; processing happens in the consumer (Phase 4).
 *
 * This service never updates Redis or calls analytics logic — that belongs
 * in the consumer.
 */
@Service
public class EventsService {

    private static final Logger log = LoggerFactory.getLogger(EventsService.class);
    private static final Set<String> VALID_STATUSES = Set.of("PENDING", "PROCESSED", "FAILED");

    private final EventRepository eventRepository;
    private final RabbitTemplate rabbitTemplate;

    public EventsService(EventRepository eventRepository, RabbitTemplate rabbitTemplate) {
        this.eventRepository = eventRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Ingests one event: persists it as PENDING, then publishes to RabbitMQ.
     *
     * @param request validated payload from the controller
     * @return acceptance DTO containing the new event ID
     */
    @Transactional
    public EventAcceptedResponse ingest(CreateEventRequest request) {
        // 1. Persist PENDING record
        Event event = new Event();
        event.setEventType(request.eventType());
        event.setUserId(request.userId());
        event.setSource(request.source());
        event.setMetadata(request.metadata());
        event.setStatus("PENDING");

        event = eventRepository.save(event);
        long eventId = event.getId();

        log.info("event_id={} type={} source={} status=PENDING — persisted, publishing to queue",
                eventId, event.getEventType(), event.getSource());

        // 2. Build and publish message
        EventMessage message = new EventMessage(
                eventId,
                event.getEventType(),
                event.getUserId(),
                event.getSource(),
                event.getMetadata(),
                event.getReceivedAt()
        );

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitTemplate.convertAndSend(
                        RabbitMqConfig.EXCHANGE_NAME,
                        RabbitMqConfig.ROUTING_KEY,
                        message
                );
                log.info("event_id={} published to exchange={} routing_key={}",
                        eventId, RabbitMqConfig.EXCHANGE_NAME, RabbitMqConfig.ROUTING_KEY);
            }
        });

        return new EventAcceptedResponse(eventId, "PENDING", "Event accepted and queued for processing");
    }

    public Page<EventResponse> getEvents(
            EventType eventType,
            String status,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            Pageable pageable) {
        if (status != null && !status.isBlank() && !VALID_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid status. Allowed values: PENDING, PROCESSED, FAILED");
        }
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom must be before or equal to dateTo");
        }

        Specification<Event> spec = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (eventType != null) {
                predicates.add(cb.equal(root.get("eventType"), eventType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("receivedAt"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("receivedAt"), dateTo));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return eventRepository.findAll(spec, pageable).map(this::toResponse);
    }

    public EventResponse getEvent(Long id) {
        return eventRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    private EventResponse toResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getEventType(),
                event.getUserId(),
                event.getSource(),
                event.getMetadata(),
                event.getReceivedAt(),
                event.getProcessedAt(),
                event.getStatus()
        );
    }
}
