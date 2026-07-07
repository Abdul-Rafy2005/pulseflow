package com.pulseflow.backend.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseflow.backend.auth.dto.LoginRequest;
import com.pulseflow.backend.auth.dto.RegisterRequest;
import com.pulseflow.backend.common.EventType;
import com.pulseflow.backend.events.dto.CreateEventRequest;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class EventIngestionIntegrationTest {

    private static final String API_KEY = "integration-test-api-key";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
            .withDatabaseName("pulseflow")
            .withUsername("pulseflow")
            .withPassword("pulseflow");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("app.events.api-key", () -> API_KEY);
        registry.add("app.events.rate-limit.max-requests", () -> "1000");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    private String jwtToken;

    @BeforeEach
    void registerAdminAndLogin() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("itadmin", "itadmin@example.com", "password123");
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)));

        LoginRequest loginRequest = new LoginRequest("itadmin", "password123");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        jwtToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();
    }

    private void waitUntilProcessed(long eventId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            Event event = eventRepository.findById(eventId).orElse(null);
            if (event != null && "PROCESSED".equals(event.getStatus())) {
                return;
            }
            Thread.sleep(250);
        }
        Event event = eventRepository.findById(eventId).orElseThrow();
        assertThat(event.getStatus()).isEqualTo("PROCESSED");
    }

    private void waitUntilProcessedCount(String source, long expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            long count = eventRepository.findAll().stream()
                    .filter(e -> source.equals(e.getSource()))
                    .filter(e -> "PROCESSED".equals(e.getStatus()))
                    .count();
            if (count >= expected) {
                return;
            }
            Thread.sleep(500);
        }
        long count = eventRepository.findAll().stream()
                .filter(e -> source.equals(e.getSource()))
                .filter(e -> "PROCESSED".equals(e.getStatus()))
                .count();
        assertThat(count).isGreaterThanOrEqualTo(expected);
    }

    @Test
    void ingestEvent_isConsumedAndQueryable() throws Exception {
        CreateEventRequest request = new CreateEventRequest(
                EventType.PAGE_VIEW, 42L, "integration-test", Map.of("page", "/home"));

        MvcResult ingestResult = mockMvc.perform(post("/events")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        long eventId = objectMapper.readTree(ingestResult.getResponse().getContentAsString())
                .get("eventId").asLong();

        waitUntilProcessed(eventId);

        mockMvc.perform(get("/events/{id}", eventId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId))
                .andExpect(jsonPath("$.eventType").value("PAGE_VIEW"))
                .andExpect(jsonPath("$.status").value("PROCESSED"));
    }

    @Test
    void listEvents_supportsFilteringAndPagination() throws Exception {
        for (int i = 0; i < 3; i++) {
            CreateEventRequest request = new CreateEventRequest(
                    EventType.LOGIN, (long) i, "filter-test", null);
            mockMvc.perform(post("/events")
                            .header("X-API-Key", API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted());
        }

        waitUntilProcessedCount("filter-test", 3);

        mockMvc.perform(get("/events")
                        .param("eventType", "LOGIN")
                        .param("status", "PROCESSED")
                        .param("page", "0")
                        .param("size", "2")
                        .param("sort", "receivedAt,desc")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.size").value(2));
    }
}
