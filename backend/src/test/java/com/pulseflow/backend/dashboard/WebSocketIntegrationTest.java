package com.pulseflow.backend.dashboard;

import com.pulseflow.backend.auth.JwtService;
import com.pulseflow.backend.auth.UserRepository;
import com.pulseflow.backend.events.EventRepository;
import com.pulseflow.backend.events.FailedEventRepository;
import com.pulseflow.backend.monitoring.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
        }
)
class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private EventRepository eventRepository;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private FailedEventRepository failedEventRepository;

    @MockBean
    private AuditLogRepository auditLogRepository;

    @MockBean
    private AmqpAdmin amqpAdmin;

    @org.springframework.beans.factory.annotation.Autowired
    private EventBroadcastService broadcastService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);

        this.stompClient = new WebSocketStompClient(sockJsClient);
        org.springframework.messaging.converter.MappingJackson2MessageConverter converter = 
                new org.springframework.messaging.converter.MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        this.stompClient.setMessageConverter(converter);
    }

    @Test
    void testConnectAndReceiveStatsBroadcast() throws Exception {
        BlockingQueue<EventBroadcastService.StatsBroadcast> statsQueue = new LinkedBlockingDeque<>();
        BlockingQueue<EventBroadcastService.EventBroadcast> eventQueue = new LinkedBlockingDeque<>();

        String url = "ws://localhost:" + port + "/ws";
        StompSession session = stompClient.connectAsync(url, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        assertNotNull(session);

        session.subscribe("/topic/stats", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return EventBroadcastService.StatsBroadcast.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                statsQueue.offer((EventBroadcastService.StatsBroadcast) payload);
            }
        });

        session.subscribe("/topic/events", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return EventBroadcastService.EventBroadcast.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                eventQueue.offer((EventBroadcastService.EventBroadcast) payload);
            }
        });

        // Wait for connection to settle
        Thread.sleep(500);

        com.pulseflow.backend.events.dto.EventMessage msg = new com.pulseflow.backend.events.dto.EventMessage(
                123L,
                com.pulseflow.backend.common.EventType.LOGIN,
                456L,
                "test-source",
                java.util.Map.of("ip", "127.0.0.1"),
                java.time.LocalDateTime.now()
        );

        broadcastService.broadcastEvent(msg, 10L, 5L);

        // Verify message received on stats queue
        EventBroadcastService.StatsBroadcast stats = statsQueue.poll(5, TimeUnit.SECONDS);
        assertNotNull(stats);
        assertEquals(10L, stats.totalEventsToday());
        assertEquals(5L, stats.activeUsersToday());

        // Verify message received on event queue
        EventBroadcastService.EventBroadcast event = eventQueue.poll(5, TimeUnit.SECONDS);
        assertNotNull(event);
        assertEquals(123L, event.eventId());
        assertEquals("LOGIN", event.eventType());
        assertEquals(456L, event.userId());
        assertEquals("test-source", event.source());
    }
}
