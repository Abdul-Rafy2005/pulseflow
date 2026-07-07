package com.pulseflow.backend.events;

import com.pulseflow.backend.common.EventType;
import com.pulseflow.backend.events.dto.CreateEventRequest;
import com.pulseflow.backend.events.dto.EventAcceptedResponse;
import com.pulseflow.backend.events.dto.EventMessage;
import com.pulseflow.backend.queue.RabbitMqConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventsServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private EventsService eventsService;

    private Event savedEvent;

    @BeforeEach
    void setUp() {
        savedEvent = new Event();
        savedEvent.setId(42L);
        savedEvent.setEventType(EventType.VIDEO_PLAY);
        savedEvent.setUserId(23L);
        savedEvent.setSource("netflix-clone");
        savedEvent.setMetadata(Map.of("device", "Desktop"));
        savedEvent.setStatus("PENDING");
        savedEvent.setReceivedAt(LocalDateTime.now());
    }

    @Test
    void ingest_happyPath_persistsAndPublishes() {
        when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);

        CreateEventRequest request = new CreateEventRequest(
                EventType.VIDEO_PLAY,
                23L,
                "netflix-clone",
                Map.of("device", "Desktop")
        );

        EventAcceptedResponse response = eventsService.ingest(request);

        // Verify response
        assertThat(response.eventId()).isEqualTo(42L);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.message()).isNotBlank();

        // Verify the saved entity had correct fields before publish
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());
        Event persisted = eventCaptor.getValue();
        assertThat(persisted.getEventType()).isEqualTo(EventType.VIDEO_PLAY);
        assertThat(persisted.getSource()).isEqualTo("netflix-clone");
        assertThat(persisted.getStatus()).isEqualTo("PENDING");

        // Verify RabbitMQ publish
        ArgumentCaptor<EventMessage> msgCaptor = ArgumentCaptor.forClass(EventMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.EXCHANGE_NAME),
                eq(RabbitMqConfig.ROUTING_KEY),
                msgCaptor.capture()
        );
        EventMessage published = msgCaptor.getValue();
        assertThat(published.eventId()).isEqualTo(42L);
        assertThat(published.eventType()).isEqualTo(EventType.VIDEO_PLAY);
        assertThat(published.source()).isEqualTo("netflix-clone");
    }

    @Test
    void ingest_nullableFieldsAreAllowed() {
        Event minimalEvent = new Event();
        minimalEvent.setId(1L);
        minimalEvent.setEventType(EventType.LOGIN);
        minimalEvent.setStatus("PENDING");
        minimalEvent.setReceivedAt(LocalDateTime.now());

        when(eventRepository.save(any(Event.class))).thenReturn(minimalEvent);

        // userId, source, metadata are all nullable per PRD §6.2
        CreateEventRequest request = new CreateEventRequest(EventType.LOGIN, null, null, null);

        EventAcceptedResponse response = eventsService.ingest(request);

        assertThat(response.eventId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("PENDING");

        ArgumentCaptor<EventMessage> msgCaptor = ArgumentCaptor.forClass(EventMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.EXCHANGE_NAME),
                eq(RabbitMqConfig.ROUTING_KEY),
                msgCaptor.capture()
        );
        assertThat(msgCaptor.getValue().userId()).isNull();
        assertThat(msgCaptor.getValue().source()).isNull();
        assertThat(msgCaptor.getValue().metadata()).isNull();
    }
}
