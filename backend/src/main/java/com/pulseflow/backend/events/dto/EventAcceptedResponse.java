package com.pulseflow.backend.events.dto;

public record EventAcceptedResponse(
        Long eventId,
        String status,
        String message
) {
}
