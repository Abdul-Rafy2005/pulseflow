package com.pulseflow.backend.monitoring.dto;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long adminId,
        String action,
        String details,
        LocalDateTime createdAt
) {
}
