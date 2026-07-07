package com.pulseflow.backend.events.dto;

import com.pulseflow.backend.common.EventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateEventRequest(
        @NotNull(message = "must not be null")
        EventType eventType,

        Long userId,

        @Size(max = 100, message = "must be 100 characters or fewer")
        String source,

        Map<String, Object> metadata
) {
}
