package com.pulseflow.backend.events;

import com.pulseflow.backend.common.EventType;
import com.pulseflow.backend.events.dto.CreateEventRequest;
import com.pulseflow.backend.events.dto.EventAcceptedResponse;
import com.pulseflow.backend.events.dto.EventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Events ingestion endpoint — the entry point for client applications.
 *
 * <p>Authentication: X-API-Key header (not JWT — this is machine-to-machine).
 * See PRD §9.2. The key is validated before the payload is processed.
 */
@RestController
@RequestMapping(value = "/events", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Events", description = "Event ingestion endpoints for client applications")
public class EventsController {

    private final EventsService eventsService;
    private final EventsApiKeyProvider apiKeyProvider;
    private final EventsRateLimitService rateLimitService;

    public EventsController(EventsService eventsService,
                            EventsApiKeyProvider apiKeyProvider,
                            EventsRateLimitService rateLimitService) {
        this.eventsService = eventsService;
        this.apiKeyProvider = apiKeyProvider;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping
    @Operation(summary = "Ingest a new event",
            description = "Accepts an event payload and immediately queues it for processing")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Event accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid payload"),
            @ApiResponse(responseCode = "401", description = "Invalid API key"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<EventAcceptedResponse> ingestEvent(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @Valid @RequestBody CreateEventRequest request) {

        if (!apiKeyProvider.isValid(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!rateLimitService.tryConsume(apiKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Please try again later.");
        }

        EventAcceptedResponse response = eventsService.ingest(request);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping
    @Operation(summary = "List events",
            description = "Paginated, filterable list. Supports eventType, status, dateFrom, dateTo. "
                    + "Sort via sort=receivedAt,desc (default). Page via page and size.")
    @SecurityRequirement(name = "bearerAuth")
    public Page<EventResponse> getEvents(
            @RequestParam(required = false) EventType eventType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @ParameterObject @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return eventsService.getEvents(eventType, status, dateFrom, dateTo, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get event details",
            description = "Returns the details of a single event by ID.")
    @SecurityRequirement(name = "bearerAuth")
    public EventResponse getEvent(@PathVariable Long id) {
        return eventsService.getEvent(id);
    }
}
