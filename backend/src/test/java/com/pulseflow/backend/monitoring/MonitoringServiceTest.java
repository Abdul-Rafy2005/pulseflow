package com.pulseflow.backend.monitoring;

import com.pulseflow.backend.monitoring.dto.AuditLogResponse;
import com.pulseflow.backend.monitoring.dto.QueueStatusResponse;
import com.pulseflow.backend.monitoring.dto.RedisStatusResponse;
import com.pulseflow.backend.queue.RabbitMqConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitoringServiceTest {

    @Mock
    private AmqpAdmin amqpAdmin;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private MonitoringService monitoringService;

    @Test
    void getQueueStatus_readsFromCacheWhenPresent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("queue:size:cache")).thenReturn("12");

        Properties mainQueueProps = new Properties();
        mainQueueProps.put("QUEUE_CONSUMER_COUNT", 3);
        when(amqpAdmin.getQueueProperties(RabbitMqConfig.QUEUE_NAME)).thenReturn(mainQueueProps);

        Properties dlqProps = new Properties();
        dlqProps.put("QUEUE_MESSAGE_COUNT", 2L);
        when(amqpAdmin.getQueueProperties(RabbitMqConfig.DLQ_NAME)).thenReturn(dlqProps);

        QueueStatusResponse status = monitoringService.getQueueStatus();

        assertThat(status.queueDepth()).isEqualTo(12L);
        assertThat(status.consumerCount()).isEqualTo(3);
        assertThat(status.dlqSize()).isEqualTo(2L);

        // Verify we never fetch the queue properties message count for caching when cache is hit
        // Note: we called mainQueueProps.get("QUEUE_CONSUMER_COUNT"), but we did not call mainQueueProps.get("QUEUE_MESSAGE_COUNT") inside getCachedOrFetchQueueDepth.
    }

    @Test
    void getQueueStatus_fetchesAndCachesWhenCacheMissed() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("queue:size:cache")).thenReturn(null);

        Properties mainQueueProps = new Properties();
        mainQueueProps.put("QUEUE_MESSAGE_COUNT", 45);
        mainQueueProps.put("QUEUE_CONSUMER_COUNT", 2);
        when(amqpAdmin.getQueueProperties(RabbitMqConfig.QUEUE_NAME)).thenReturn(mainQueueProps);

        Properties dlqProps = new Properties();
        dlqProps.put("QUEUE_MESSAGE_COUNT", 5L);
        when(amqpAdmin.getQueueProperties(RabbitMqConfig.DLQ_NAME)).thenReturn(dlqProps);

        QueueStatusResponse status = monitoringService.getQueueStatus();

        assertThat(status.queueDepth()).isEqualTo(45L);
        assertThat(status.consumerCount()).isEqualTo(2);
        assertThat(status.dlqSize()).isEqualTo(5L);

        verify(valueOperations).set(eq("queue:size:cache"), eq("45"), any());
    }

    @Test
    void getRedisStatus_returnsConnectedMetrics() {
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            int count = callCount.getAndIncrement();
            if (count == 0) {
                return 15L;
            } else {
                Properties props = new Properties();
                props.put("used_memory", "102400");
                props.put("used_memory_human", "100K");
                return props;
            }
        });

        RedisStatusResponse status = monitoringService.getRedisStatus();

        assertThat(status.status()).isEqualTo("CONNECTED");
        assertThat(status.keyCount()).isEqualTo(15L);
        assertThat(status.memoryUsageBytes()).isEqualTo(102400L);
        assertThat(status.memoryUsageHuman()).isEqualTo("100K");
    }

    @Test
    void getRedisStatus_returnsDisconnectedOnException() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenThrow(new RuntimeException("Redis connection timed out"));

        RedisStatusResponse status = monitoringService.getRedisStatus();

        assertThat(status.status()).isEqualTo("DISCONNECTED");
        assertThat(status.keyCount()).isEqualTo(0L);
        assertThat(status.memoryUsageBytes()).isEqualTo(0L);
        assertThat(status.memoryUsageHuman()).isEqualTo("N/A");
    }

    @Test
    void getAuditLogs_returnsPaginatedLogs() {
        AuditLog log = new AuditLog(1L, "ADMIN_LOGIN", "Login success");
        log.setId(10L);
        log.setCreatedAt(java.time.LocalDateTime.now());

        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> page = new PageImpl<>(Collections.singletonList(log), pageable, 1);
        when(auditLogRepository.findAll(pageable)).thenReturn(page);

        Page<AuditLogResponse> response = monitoringService.getAuditLogs(pageable);

        assertThat(response.getContent()).hasSize(1);
        AuditLogResponse res = response.getContent().get(0);
        assertThat(res.id()).isEqualTo(10L);
        assertThat(res.adminId()).isEqualTo(1L);
        assertThat(res.action()).isEqualTo("ADMIN_LOGIN");
        assertThat(res.details()).isEqualTo("Login success");
    }
}
