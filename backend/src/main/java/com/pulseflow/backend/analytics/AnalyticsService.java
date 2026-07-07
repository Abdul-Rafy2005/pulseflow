package com.pulseflow.backend.analytics;

import com.pulseflow.backend.analytics.dto.AnalyticsSummaryResponse;
import com.pulseflow.backend.analytics.dto.DailyStats;
import com.pulseflow.backend.analytics.dto.RealtimeSnapshot;
import com.pulseflow.backend.analytics.dto.TopEvent;
import com.pulseflow.backend.analytics.dto.TopUser;
import com.pulseflow.backend.events.Event;
import com.pulseflow.backend.events.EventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final EventRepository eventRepository;
    private final StringRedisTemplate redisTemplate;
    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    public AnalyticsService(EventRepository eventRepository, StringRedisTemplate redisTemplate) {
        this.eventRepository = eventRepository;
        this.redisTemplate = redisTemplate;
    }

    public AnalyticsSummaryResponse getSummary() {
        String eventsStr = redisTemplate.opsForValue().get("stats:today:events");
        long todayEvents = eventsStr != null ? Long.parseLong(eventsStr) : 0;

        Long activeUsers = redisTemplate.opsForSet().size("stats:today:active_users");
        long todayActiveUsers = activeUsers != null ? activeUsers : 0;

        Set<String> topSearches = redisTemplate.opsForZSet().reverseRange("stats:today:top_searches", 0, 0);
        String topSearch = (topSearches != null && !topSearches.isEmpty()) ? topSearches.iterator().next() : null;

        Set<String> topCountries = redisTemplate.opsForZSet().reverseRange("stats:top_countries", 0, 0);
        String topCountry = (topCountries != null && !topCountries.isEmpty()) ? topCountries.iterator().next() : null;

        Set<String> mostViewedPages = redisTemplate.opsForZSet().reverseRange("stats:most_viewed_page", 0, 0);
        String mostViewedPage = (mostViewedPages != null && !mostViewedPages.isEmpty()) ? mostViewedPages.iterator().next() : null;

        return new AnalyticsSummaryResponse(todayEvents, todayActiveUsers, topSearch, topCountry, mostViewedPage);
    }

    public List<DailyStats> getDailyStats(int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        return eventRepository.countEventsByDate(startDate);
    }

    public List<TopEvent> getTopEvents(int limit) {
        return eventRepository.findTopEvents(PageRequest.of(0, limit));
    }

    public List<TopUser> getTopUsers(int limit) {
        return eventRepository.findTopUsers(PageRequest.of(0, limit));
    }

    public RealtimeSnapshot getRealtimeSnapshot() {
        AnalyticsSummaryResponse summary = getSummary();
        
        String currentMinute = LocalDateTime.now().format(MINUTE_FORMATTER);
        String rateStr = redisTemplate.opsForValue().get("stats:events_per_minute:" + currentMinute);
        long processingRatePerMinute = rateStr != null ? Long.parseLong(rateStr) : 0;
        
        // Queue size would ideally be fetched from RabbitMQ Mgmt API, but PRD says Phase 6 monitoring module
        // We will return 0 here and update it fully in Phase 6. Or we check the Redis cache if it exists.
        String queueSizeStr = redisTemplate.opsForValue().get("queue:size:cache");
        long queueSize = queueSizeStr != null ? Long.parseLong(queueSizeStr) : 0;

        List<Event> recentEvents = eventRepository.findTop50ByOrderByReceivedAtDesc();

        return new RealtimeSnapshot(summary, queueSize, processingRatePerMinute, recentEvents);
    }
}
