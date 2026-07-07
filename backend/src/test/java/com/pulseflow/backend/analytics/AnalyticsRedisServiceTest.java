package com.pulseflow.backend.analytics;

import com.pulseflow.backend.common.EventType;
import com.pulseflow.backend.events.dto.EventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsRedisServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private SetOperations<String, String> setOps;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    private AnalyticsRedisService redisService;

    @BeforeEach
    void setUp() {
        // Strict stubbing might complain if we don't use all ops in every test, but we can configure leniency or just stub what's needed.
        // We'll stub leniency dynamically in tests if required, or just stub them.
    }

    @Test
    void recordEvent_updatesRedisCounters() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(setOps.add(anyString(), anyString())).thenReturn(1L);
        when(zSetOps.incrementScore(anyString(), anyString(), anyDouble())).thenReturn(1.0);

        redisService = new AnalyticsRedisService(redisTemplate);

        EventMessage message = new EventMessage(
                1L,
                EventType.PAGE_VIEW,
                100L,
                "app",
                Map.of("page", "/dashboard", "country", "UK"),
                LocalDateTime.now()
        );

        redisService.recordEvent(message);

        verify(valueOps).increment("stats:today:events");
        verify(setOps).add("stats:today:active_users", "100");
        verify(zSetOps).incrementScore("stats:top_countries", "UK", 1.0);
        verify(zSetOps).incrementScore("stats:most_viewed_page", "/dashboard", 1.0);
    }
}
