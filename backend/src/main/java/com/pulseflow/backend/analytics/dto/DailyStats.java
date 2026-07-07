package com.pulseflow.backend.analytics.dto;

import java.time.LocalDate;

public record DailyStats(
        LocalDate date,
        long totalEvents
) {}
