package com.pulseflow.backend.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.pulseflow.backend.common.EventType;
import com.pulseflow.backend.events.dto.EventResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EventsServicePaginationTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private EventsService eventsService;

    private Event sampleEvent;

    @BeforeEach
    void setUp() {
        sampleEvent = new Event();
        sampleEvent.setId(1L);
        sampleEvent.setEventType(EventType.LOGIN);
        sampleEvent.setUserId(10L);
        sampleEvent.setSource("web");
        sampleEvent.setStatus("PROCESSED");
        sampleEvent.setReceivedAt(LocalDateTime.of(2026, 7, 1, 12, 0));
    }

    @Test
    void getEvents_mapsToDtoPage() {
        Page<Event> entityPage = new PageImpl<>(List.of(sampleEvent), PageRequest.of(0, 20), 1);
        when(eventRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(entityPage);

        Page<EventResponse> result = eventsService.getEvents(
                EventType.LOGIN, "PROCESSED", null, null, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(1L);
        assertThat(result.getContent().get(0).eventType()).isEqualTo(EventType.LOGIN);
        assertThat(result.getContent().get(0).status()).isEqualTo("PROCESSED");
    }

    @Test
    void getEvents_invalidStatus_throwsBadRequest() {
        assertThatThrownBy(() -> eventsService.getEvents(null, "UNKNOWN", null, null, PageRequest.of(0, 20)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid status");
    }

    @Test
    void getEvents_dateFromAfterDateTo_throwsBadRequest() {
        LocalDateTime from = LocalDateTime.of(2026, 7, 2, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 7, 1, 0, 0);

        assertThatThrownBy(() -> eventsService.getEvents(null, null, from, to, PageRequest.of(0, 20)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("dateFrom");
    }

    @Test
    void getEvent_notFound_throwsNotFound() {
        when(eventRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> eventsService.getEvent(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Event not found");
    }
}
