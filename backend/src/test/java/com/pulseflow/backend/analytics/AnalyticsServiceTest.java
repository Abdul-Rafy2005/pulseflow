package com.pulseflow.backend.analytics;

import com.pulseflow.backend.analytics.dto.AnalyticsSummaryResponse;
import com.pulseflow.backend.events.Event;
import com.pulseflow.backend.events.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private SetOperations<String, String> setOps;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

        analyticsService = new AnalyticsService(eventRepository, redisTemplate);
    }

    @Test
    void getSummaryReturnsDataFromRedis() {
        when(valueOps.get("stats:today:events")).thenReturn("150");
        when(setOps.size("stats:today:active_users")).thenReturn(42L);
        when(zSetOps.reverseRange("stats:today:top_searches", 0, 0)).thenReturn(Collections.singleton("shoes"));
        when(zSetOps.reverseRange("stats:top_countries", 0, 0)).thenReturn(Collections.singleton("US"));
        when(zSetOps.reverseRange("stats:most_viewed_page", 0, 0)).thenReturn(Collections.singleton("/home"));

        AnalyticsSummaryResponse summary = analyticsService.getSummary();

        assertThat(summary.todayEvents()).isEqualTo(150L);
        assertThat(summary.todayActiveUsers()).isEqualTo(42L);
        assertThat(summary.topSearch()).isEqualTo("shoes");
        assertThat(summary.topCountry()).isEqualTo("US");
        assertThat(summary.mostViewedPage()).isEqualTo("/home");
    }
}
