package com.pulseflow.backend.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class EventsRateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private EventsRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        // lenient: tryConsume_disabledWhenMaxRequestsZero creates its own instance
        // with maxRequests=0 and never reaches opsForValue().
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimitService = new EventsRateLimitService(redisTemplate, 3, 60);
    }

    @Test
    void tryConsume_underLimit_allowsRequests() {
        when(valueOperations.increment("ratelimit:events:client-a")).thenReturn(1L, 2L, 3L);
        when(redisTemplate.expire(eq("ratelimit:events:client-a"), any(Duration.class))).thenReturn(true);

        assertThat(rateLimitService.tryConsume("client-a")).isTrue();
        assertThat(rateLimitService.tryConsume("client-a")).isTrue();
        assertThat(rateLimitService.tryConsume("client-a")).isTrue();
    }

    @Test
    void tryConsume_overLimit_rejectsRequest() {
        when(valueOperations.increment("ratelimit:events:client-a")).thenReturn(4L);

        assertThat(rateLimitService.tryConsume("client-a")).isFalse();
        verify(redisTemplate, never()).expire(any(), any(Duration.class));
    }

    @Test
    void tryConsume_disabledWhenMaxRequestsZero() {
        EventsRateLimitService disabled = new EventsRateLimitService(redisTemplate, 0, 60);

        assertThat(disabled.tryConsume("client-a")).isTrue();
        verify(valueOperations, never()).increment(any());
    }
}
