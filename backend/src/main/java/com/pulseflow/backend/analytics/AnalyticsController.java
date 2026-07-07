package com.pulseflow.backend.analytics;

import com.pulseflow.backend.analytics.dto.AnalyticsSummaryResponse;
import com.pulseflow.backend.analytics.dto.DailyStats;
import com.pulseflow.backend.analytics.dto.RealtimeSnapshot;
import com.pulseflow.backend.analytics.dto.TopEvent;
import com.pulseflow.backend.analytics.dto.TopUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/analytics", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Analytics", description = "Analytics read endpoints for the dashboard")
@SecurityRequirement(name = "bearerAuth")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get today's totals (Redis-first)")
    public AnalyticsSummaryResponse getSummary() {
        return analyticsService.getSummary();
    }

    @GetMapping("/daily")
    @Operation(summary = "Get time-series daily stats")
    public List<DailyStats> getDailyStats(@RequestParam(defaultValue = "30") int days) {
        return analyticsService.getDailyStats(days);
    }

    @GetMapping("/top-events")
    @Operation(summary = "Get ranked event types")
    public List<TopEvent> getTopEvents(@RequestParam(defaultValue = "10") int limit) {
        return analyticsService.getTopEvents(limit);
    }

    @GetMapping("/top-users")
    @Operation(summary = "Get ranked top users by activity")
    public List<TopUser> getTopUsers(@RequestParam(defaultValue = "10") int limit) {
        return analyticsService.getTopUsers(limit);
    }

    @GetMapping("/realtime")
    @Operation(summary = "Get initial snapshot for dashboard init")
    public RealtimeSnapshot getRealtimeSnapshot() {
        return analyticsService.getRealtimeSnapshot();
    }
}
