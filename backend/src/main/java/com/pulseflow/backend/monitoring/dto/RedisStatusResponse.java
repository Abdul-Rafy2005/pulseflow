package com.pulseflow.backend.monitoring.dto;

public record RedisStatusResponse(
        String status,
        long keyCount,
        long memoryUsageBytes,
        String memoryUsageHuman
) {
}
