package com.pulseflow.backend.analytics.dto;

import com.pulseflow.backend.events.Event;
import java.util.List;

public record RealtimeSnapshot(
        AnalyticsSummaryResponse summary,
        long queueSize,
        long processingRatePerMinute,
        List<Event> recentEvents
) {}
