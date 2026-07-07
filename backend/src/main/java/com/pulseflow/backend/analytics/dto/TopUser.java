package com.pulseflow.backend.analytics.dto;

public record TopUser(
        Long userId,
        long eventCount
) {}
