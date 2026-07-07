package com.pulseflow.backend.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AuditLog log(Long adminId, String action, String details) {
        log.info("Audit Log: adminId={}, action={}, details={}", adminId, action, details);
        AuditLog auditLog = new AuditLog(adminId, action, details);
        return auditLogRepository.save(auditLog);
    }
}
