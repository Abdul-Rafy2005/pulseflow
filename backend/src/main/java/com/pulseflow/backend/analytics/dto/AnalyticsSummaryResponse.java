package com.pulseflow.backend.analytics.dto;

public record AnalyticsSummaryResponse(
        long todayEvents,
        long todayActiveUsers,
        String topSearch,
        String topCountry,
        String mostViewedPage
) {}
