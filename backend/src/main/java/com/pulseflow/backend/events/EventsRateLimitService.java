package com.pulseflow.backend.events;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-based fixed-window rate limiter for event ingestion.
 *
 * <p>Defaults: 100 requests per 60-second window (configurable via
 * app.events.rate-limit.max-requests and app.events.rate-limit.window-seconds).
 *
 * <p>When max-requests is 0, rate limiting is disabled (useful in tests).
 * Fail-open on Redis errors to avoid denying valid traffic if Redis is temporarily
 * unavailable.
 */
@Service
public class EventsRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(EventsRateLimitService.class);
    private static final String KEY_PREFIX = "ratelimit:events:";

    private final StringRedisTemplate redisTemplate;
    private final int maxRequests;
    private final int windowSeconds;

    public EventsRateLimitService(
            StringRedisTemplate redisTemplate,
            @Value("${app.events.rate-limit.max-requests:100}") int maxRequests,
            @Value("${app.events.rate-limit.window-seconds:60}") int windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    /**
     * Attempts to consume one request slot for the given API key.
     *
     * @param apiKey the client identifier
     * @return true if the request is within the allowed limit, false if exceeded
     */
    public boolean tryConsume(String apiKey) {
        if (maxRequests == 0) {
            return true; // Rate limiting disabled
        }
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }

        String key = KEY_PREFIX + apiKey;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                log.warn("Redis increment returned null for key={}", key);
                return true; // Fail-open
            }
            // Set TTL only on the first request in this window
            if (count == 1) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }
            if (count > maxRequests) {
                log.warn("Rate limit exceeded for apiKey={} ({}/{} in {}s window)",
                        apiKey, count, maxRequests, windowSeconds);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Redis error during rate limit check for key={}", key, e);
            return true; // Fail-open to preserve availability
        }
    }
}
