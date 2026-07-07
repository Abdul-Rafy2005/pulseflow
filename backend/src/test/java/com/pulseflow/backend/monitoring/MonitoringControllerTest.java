package com.pulseflow.backend.monitoring;

import com.pulseflow.backend.monitoring.dto.AuditLogResponse;
import com.pulseflow.backend.monitoring.dto.QueueStatusResponse;
import com.pulseflow.backend.monitoring.dto.RedisStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MonitoringController.class)
class MonitoringControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MonitoringService monitoringService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getQueueStatus_authenticated_returnsData() throws Exception {
        when(monitoringService.getQueueStatus()).thenReturn(new QueueStatusResponse(10L, 2, 1L));

        mockMvc.perform(get("/queue/status")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueDepth").value(10))
                .andExpect(jsonPath("$.consumerCount").value(2))
                .andExpect(jsonPath("$.dlqSize").value(1));
    }

    @Test
    void getQueueStatus_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/queue/status")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRedisStatus_authenticated_returnsData() throws Exception {
        when(monitoringService.getRedisStatus()).thenReturn(new RedisStatusResponse("CONNECTED", 50L, 2048L, "2K"));

        mockMvc.perform(get("/redis/status")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONNECTED"))
                .andExpect(jsonPath("$.keyCount").value(50))
                .andExpect(jsonPath("$.memoryUsageBytes").value(2048))
                .andExpect(jsonPath("$.memoryUsageHuman").value("2K"));
    }

    @Test
    void getRedisStatus_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/redis/status")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAuditLogs_authenticated_returnsPaginatedData() throws Exception {
        AuditLogResponse responseDto = new AuditLogResponse(1L, 2L, "ADMIN_LOGIN", "Login success", LocalDateTime.now());
        when(monitoringService.getAuditLogs(any())).thenReturn(new PageImpl<>(Collections.singletonList(responseDto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/monitoring/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].adminId").value(2))
                .andExpect(jsonPath("$.content[0].action").value("ADMIN_LOGIN"))
                .andExpect(jsonPath("$.content[0].details").value("Login success"));
    }

    @Test
    void getAuditLogs_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/monitoring/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
