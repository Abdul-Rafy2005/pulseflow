package com.pulseflow.backend.analytics;

import com.pulseflow.backend.common.EventType;
import com.pulseflow.backend.events.Event;
import com.pulseflow.backend.events.EventRepository;
import com.pulseflow.backend.events.FailedEvent;
import com.pulseflow.backend.events.FailedEventRepository;
import com.pulseflow.backend.events.dto.EventMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlqConsumerTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private FailedEventRepository failedEventRepository;

    private DlqConsumer dlqConsumer;

    @Test
    void processDlqMessage_marksEventFailedAndSavesFailedEvent() {
        dlqConsumer = new DlqConsumer(eventRepository, failedEventRepository, 3);

        EventMessage message = new EventMessage(
                42L, EventType.LOGIN, 1L, "test-app", null, LocalDateTime.now()
        );

        Event event = new Event();
        event.setId(42L);
        event.setStatus("PENDING");
        when(eventRepository.findById(42L)).thenReturn(Optional.of(event));

        Map<String, Object> headers = new HashMap<>();
        headers.put("x-exception-message", "NullPointerException: something went wrong");

        dlqConsumer.processDlqMessage(message, headers);

        // Verify event status set to FAILED
        verify(eventRepository).save(event);
        assertThat(event.getStatus()).isEqualTo("FAILED");

        // Verify failed_events row created
        ArgumentCaptor<FailedEvent> captor = ArgumentCaptor.forClass(FailedEvent.class);
        verify(failedEventRepository).save(captor.capture());
        FailedEvent saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(42L);
        assertThat(saved.getReason()).contains("NullPointerException");
        assertThat(saved.getRetryCount()).isEqualTo(3);
    }

    @Test
    void processDlqMessage_handlesUnknownReason() {
        dlqConsumer = new DlqConsumer(eventRepository, failedEventRepository, 3);

        EventMessage message = new EventMessage(
                99L, EventType.PURCHASE, 5L, "shop", null, LocalDateTime.now()
        );

        Event event = new Event();
        event.setId(99L);
        event.setStatus("PENDING");
        when(eventRepository.findById(99L)).thenReturn(Optional.of(event));

        // No exception headers at all
        Map<String, Object> headers = new HashMap<>();

        dlqConsumer.processDlqMessage(message, headers);

        ArgumentCaptor<FailedEvent> captor = ArgumentCaptor.forClass(FailedEvent.class);
        verify(failedEventRepository).save(captor.capture());
        assertThat(captor.getValue().getReason()).contains("Unknown failure reason");
    }
}
