package com.pulseflow.backend.events.dto;

import com.pulseflow.backend.common.EventType;
import java.time.LocalDateTime;
import java.util.Map;

public record EventResponse(
        Long id,
        EventType eventType,
        Long userId,
        String source,
        Map<String, Object> metadata,
        LocalDateTime receivedAt,
        LocalDateTime processedAt,
        String status
) {
}