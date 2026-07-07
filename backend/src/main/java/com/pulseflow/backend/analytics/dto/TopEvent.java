package com.pulseflow.backend.analytics.dto;

public record TopEvent(
        String eventType,
        long count
) {}
