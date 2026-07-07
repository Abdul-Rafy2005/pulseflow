package com.pulseflow.backend.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseflow.backend.common.EventType;
import com.pulseflow.backend.events.dto.CreateEventRequest;
import com.pulseflow.backend.events.dto.EventAcceptedResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EventsController.class)
@TestPropertySource(properties = "app.events.api-key=test-api-key")
class EventsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventsService eventsService;

    @MockBean
    private EventsApiKeyProvider apiKeyProvider;

    @MockBean
    private EventsRateLimitService rateLimitService;

    @Test
    @WithMockUser
    void postEvent_validRequestAndKey_returns202() throws Exception {
        when(apiKeyProvider.isValid("valid-key")).thenReturn(true);
        when(rateLimitService.tryConsume("valid-key")).thenReturn(true);
        when(eventsService.ingest(any(CreateEventRequest.class)))
                .thenReturn(new EventAcceptedResponse(1L, "PENDING", "Event accepted and queued for processing"));

        CreateEventRequest request = new CreateEventRequest(
                EventType.VIDEO_PLAY, 23L, "netflix-clone", Map.of("device", "Desktop"));

        mockMvc.perform(post("/events")
                        .header("X-API-Key", "valid-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser
    void postEvent_missingApiKey_returns401() throws Exception {
        when(apiKeyProvider.isValid(null)).thenReturn(false);

        CreateEventRequest request = new CreateEventRequest(
                EventType.LOGIN, null, null, null);

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(eventsService, never()).ingest(any());
    }

    @Test
    @WithMockUser
    void postEvent_wrongApiKey_returns401() throws Exception {
        when(apiKeyProvider.isValid("wrong-key")).thenReturn(false);

        CreateEventRequest request = new CreateEventRequest(
                EventType.LOGIN, null, null, null);

        mockMvc.perform(post("/events")
                        .header("X-API-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(eventsService, never()).ingest(any());
    }

    @Test
    @WithMockUser
    void postEvent_missingEventType_returns400() throws Exception {
        when(apiKeyProvider.isValid("valid-key")).thenReturn(true);

        // null eventType — @NotNull should trigger validation failure
        String badPayload = "{\"userId\": 1, \"source\": \"app\"}";

        mockMvc.perform(post("/events")
                        .header("X-API-Key", "valid-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPayload)
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(eventsService, never()).ingest(any());
    }

    @Test
    @WithMockUser
    void postEvent_invalidEventType_returns400() throws Exception {
        when(apiKeyProvider.isValid("valid-key")).thenReturn(true);

        // Unknown enum value — Jackson will fail to deserialize
        String badPayload = "{\"eventType\": \"INVALID_TYPE\", \"userId\": 1}";

        mockMvc.perform(post("/events")
                        .header("X-API-Key", "valid-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPayload)
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(eventsService, never()).ingest(any());
    }

    @Test
    @WithMockUser
    void postEvent_rateLimitExceeded_returns429() throws Exception {
        when(apiKeyProvider.isValid("valid-key")).thenReturn(true);
        when(rateLimitService.tryConsume("valid-key")).thenReturn(false);

        CreateEventRequest request = new CreateEventRequest(
                EventType.VIDEO_PLAY, 23L, "netflix-clone", Map.of("device", "Desktop"));

        mockMvc.perform(post("/events")
                        .header("X-API-Key", "valid-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isTooManyRequests());

        verify(eventsService, never()).ingest(any());
    }
}
