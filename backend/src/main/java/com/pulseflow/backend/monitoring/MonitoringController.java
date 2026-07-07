package com.pulseflow.backend.monitoring;

import com.pulseflow.backend.monitoring.dto.AuditLogResponse;
import com.pulseflow.backend.monitoring.dto.QueueStatusResponse;
import com.pulseflow.backend.monitoring.dto.RedisStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Monitoring", description = "Endpoints for monitoring system status and audit logs")
@SecurityRequirement(name = "bearerAuth")
public class MonitoringController {

    private final MonitoringService monitoringService;

    public MonitoringController(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping(value = "/queue/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get queue status", description = "Returns the depth, consumer count, and DLQ size of events queue")
    public QueueStatusResponse getQueueStatus() {
        return monitoringService.getQueueStatus();
    }

    @GetMapping(value = "/redis/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Redis status", description = "Returns key count, memory usage, and connection status of Redis")
    public RedisStatusResponse getRedisStatus() {
        return monitoringService.getRedisStatus();
    }

    @GetMapping(value = "/monitoring/audit-logs", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get audit logs", description = "Returns a paginated list of audit logs (JWT required)")
    public Page<AuditLogResponse> getAuditLogs(@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return monitoringService.getAuditLogs(pageable);
    }
}
