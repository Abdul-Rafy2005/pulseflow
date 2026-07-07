package com.pulseflow.backend.analytics;

import com.pulseflow.backend.common.EventType;
import com.pulseflow.backend.events.dto.EventMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
public class AnalyticsRedisService {

    private final StringRedisTemplate redisTemplate;
    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    public AnalyticsRedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void recordEvent(EventMessage event) {
        LocalDateTime now = LocalDateTime.now();
        Duration untilMidnight = Duration.between(now, now.toLocalDate().atTime(LocalTime.MAX));
        Duration oneDay = Duration.ofDays(1);

        // 1. Total events today
        String todayEventsKey = "stats:today:events";
        Long newCount = redisTemplate.opsForValue().increment(todayEventsKey);
        if (newCount != null && newCount == 1) {
            redisTemplate.expire(todayEventsKey, untilMidnight);
        }

        // 2. Active users today
        if (event.userId() != null) {
            String activeUsersKey = "stats:today:active_users";
            Long added = redisTemplate.opsForSet().add(activeUsersKey, String.valueOf(event.userId()));
            if (added != null && added == 1) {
                // Ensure expiration is set (might set it multiple times, but safe)
                redisTemplate.expire(activeUsersKey, untilMidnight);
            }
        }

        // 3. Events per minute
        String currentMinute = now.format(MINUTE_FORMATTER);
        String eventsPerMinuteKey = "stats:events_per_minute:" + currentMinute;
        Long minuteCount = redisTemplate.opsForValue().increment(eventsPerMinuteKey);
        if (minuteCount != null && minuteCount == 1) {
            redisTemplate.expire(eventsPerMinuteKey, Duration.ofMinutes(2));
        }

        Map<String, Object> metadata = event.metadata();
        if (metadata != null) {
            // 4. Top searches
            if (event.eventType() == EventType.SEARCH && metadata.containsKey("query")) {
                String topSearchesKey = "stats:today:top_searches";
                redisTemplate.opsForZSet().incrementScore(topSearchesKey, String.valueOf(metadata.get("query")), 1.0);
                redisTemplate.expire(topSearchesKey, oneDay); // Keep rolling 24h or until midnight (PRD says 24h)
            }

            // 5. Top countries
            if (metadata.containsKey("country")) {
                String topCountriesKey = "stats:top_countries";
                redisTemplate.opsForZSet().incrementScore(topCountriesKey, String.valueOf(metadata.get("country")), 1.0);
                redisTemplate.expire(topCountriesKey, oneDay);
            }

            // 6. Most viewed page
            if (event.eventType() == EventType.PAGE_VIEW && metadata.containsKey("page")) {
                String mostViewedPageKey = "stats:most_viewed_page";
                redisTemplate.opsForZSet().incrementScore(mostViewedPageKey, String.valueOf(metadata.get("page")), 1.0);
                redisTemplate.expire(mostViewedPageKey, oneDay);
            }
        }
    }

    /**
     * Returns the current total events today counter from Redis.
     */
    public long getTotalEventsToday() {
        String val = redisTemplate.opsForValue().get("stats:today:events");
        return val != null ? Long.parseLong(val) : 0;
    }

    /**
     * Returns the current active users today count from Redis.
     */
    public long getActiveUsersToday() {
        Long size = redisTemplate.opsForSet().size("stats:today:active_users");
        return size != null ? size : 0;
    }
}
