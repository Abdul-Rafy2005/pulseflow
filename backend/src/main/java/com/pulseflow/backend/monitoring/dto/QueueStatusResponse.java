package com.pulseflow.backend.monitoring.dto;

public record QueueStatusResponse(
        long queueDepth,
        int consumerCount,
        long dlqSize
) {
}
