package com.pulseflow.backend.monitoring;

import com.pulseflow.backend.monitoring.dto.AuditLogResponse;
import com.pulseflow.backend.monitoring.dto.QueueStatusResponse;
import com.pulseflow.backend.monitoring.dto.RedisStatusResponse;
import com.pulseflow.backend.queue.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Properties;

@Service
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);
    private static final String QUEUE_SIZE_CACHE_KEY = "queue:size:cache";

    private final AmqpAdmin amqpAdmin;
    private final StringRedisTemplate redisTemplate;
    private final AuditLogRepository auditLogRepository;

    public MonitoringService(AmqpAdmin amqpAdmin,
                             StringRedisTemplate redisTemplate,
                             AuditLogRepository auditLogRepository) {
        this.amqpAdmin = amqpAdmin;
        this.redisTemplate = redisTemplate;
        this.auditLogRepository = auditLogRepository;
    }

    public QueueStatusResponse getQueueStatus() {
        long queueDepth = getCachedOrFetchQueueDepth();
        int consumerCount = 0;
        long dlqSize = 0;

        try {
            Properties mainQueueProps = amqpAdmin.getQueueProperties(RabbitMqConfig.QUEUE_NAME);
            if (mainQueueProps != null) {
                Object consumerCountObj = mainQueueProps.get("QUEUE_CONSUMER_COUNT");
                if (consumerCountObj instanceof Number) {
                    consumerCount = ((Number) consumerCountObj).intValue();
                }
            }

            Properties dlqProps = amqpAdmin.getQueueProperties(RabbitMqConfig.DLQ_NAME);
            if (dlqProps != null) {
                Object dlqSizeObj = dlqProps.get("QUEUE_MESSAGE_COUNT");
                if (dlqSizeObj instanceof Number) {
                    dlqSize = ((Number) dlqSizeObj).longValue();
                }
            }
        } catch (Exception e) {
            log.error("Failed to query RabbitMQ queue properties", e);
        }

        return new QueueStatusResponse(queueDepth, consumerCount, dlqSize);
    }

    private long getCachedOrFetchQueueDepth() {
        try {
            String cachedDepth = redisTemplate.opsForValue().get(QUEUE_SIZE_CACHE_KEY);
            if (cachedDepth != null) {
                return Long.parseLong(cachedDepth);
            }
        } catch (Exception e) {
            log.warn("Failed to read queue size cache from Redis", e);
        }

        long queueDepth = 0;
        try {
            Properties mainQueueProps = amqpAdmin.getQueueProperties(RabbitMqConfig.QUEUE_NAME);
            if (mainQueueProps != null) {
                Object queueDepthObj = mainQueueProps.get("QUEUE_MESSAGE_COUNT");
                if (queueDepthObj instanceof Number) {
                    queueDepth = ((Number) queueDepthObj).longValue();
                }
            }

            try {
                redisTemplate.opsForValue().set(QUEUE_SIZE_CACHE_KEY, String.valueOf(queueDepth), Duration.ofSeconds(5));
            } catch (Exception e) {
                log.warn("Failed to write queue size cache to Redis", e);
            }
        } catch (Exception e) {
            log.error("Failed to fetch main queue depth from RabbitMQ", e);
        }

        return queueDepth;
    }

    public RedisStatusResponse getRedisStatus() {
        try {
            Long keyCount = redisTemplate.execute((RedisCallback<Long>) connection -> connection.serverCommands().dbSize());
            if (keyCount == null) {
                keyCount = 0L;
            }

            Properties memoryInfo = redisTemplate.execute((RedisCallback<Properties>) connection -> connection.serverCommands().info("memory"));
            long memoryBytes = 0;
            String memoryHuman = "N/A";

            if (memoryInfo != null) {
                String usedMemory = memoryInfo.getProperty("used_memory");
                if (usedMemory != null) {
                    try {
                        memoryBytes = Long.parseLong(usedMemory.trim());
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse Redis memory bytes: {}", usedMemory);
                    }
                }
                String usedMemoryHuman = memoryInfo.getProperty("used_memory_human");
                if (usedMemoryHuman != null) {
                    memoryHuman = usedMemoryHuman.trim();
                }
            }

            return new RedisStatusResponse("CONNECTED", keyCount, memoryBytes, memoryHuman);
        } catch (Exception e) {
            log.error("Redis status check failed", e);
            return new RedisStatusResponse("DISCONNECTED", 0L, 0L, "N/A");
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable)
                .map(log -> new AuditLogResponse(
                        log.getId(),
                        log.getAdminId(),
                        log.getAction(),
                        log.getDetails(),
                        log.getCreatedAt()
                ));
    }
}
