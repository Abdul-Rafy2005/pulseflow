package com.pulseflow.backend.dashboard;

import com.pulseflow.backend.common.EventType;
import com.pulseflow.backend.events.dto.EventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class EventBroadcastServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private EventBroadcastService broadcastService;

    @BeforeEach
    void setUp() {
        broadcastService = new EventBroadcastService(messagingTemplate);
    }

    @Test
    void broadcastEvent_sendsToEventsTopic() {
        EventMessage message = new EventMessage(
                42L, EventType.BUTTON_CLICK, 10L, "web-app",
                Map.of("button", "signup"), LocalDateTime.now()
        );

        broadcastService.broadcastEvent(message, 100L, 25L);

        ArgumentCaptor<EventBroadcastService.EventBroadcast> captor =
                ArgumentCaptor.forClass(EventBroadcastService.EventBroadcast.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/events"), captor.capture());

        EventBroadcastService.EventBroadcast payload = captor.getValue();
        assertEquals(42L, payload.eventId());
        assertEquals("BUTTON_CLICK", payload.eventType());
        assertEquals(10L, payload.userId());
        assertEquals("web-app", payload.source());
        assertNotNull(payload.processedAt());
    }

    @Test
    void broadcastEvent_sendsToStatsTopic() {
        EventMessage message = new EventMessage(
                99L, EventType.PAGE_VIEW, 5L, "mobile",
                Map.of("page", "/home"), LocalDateTime.now()
        );

        broadcastService.broadcastEvent(message, 500L, 120L);

        ArgumentCaptor<EventBroadcastService.StatsBroadcast> captor =
                ArgumentCaptor.forClass(EventBroadcastService.StatsBroadcast.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/stats"), captor.capture());

        EventBroadcastService.StatsBroadcast payload = captor.getValue();
        assertEquals(500L, payload.totalEventsToday());
        assertEquals(120L, payload.activeUsersToday());
        assertNotNull(payload.timestamp());
    }

    @Test
    void broadcastEvent_sendsBothTopics() {
        EventMessage message = new EventMessage(
                1L, EventType.SEARCH, 1L, "api",
                Map.of("query", "test"), LocalDateTime.now()
        );

        broadcastService.broadcastEvent(message, 1L, 1L);

        verify(messagingTemplate).convertAndSend(eq("/topic/events"),
                org.mockito.ArgumentMatchers.any(EventBroadcastService.EventBroadcast.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/stats"),
                org.mockito.ArgumentMatchers.any(EventBroadcastService.StatsBroadcast.class));
        verifyNoMoreInteractions(messagingTemplate);
    }
}
