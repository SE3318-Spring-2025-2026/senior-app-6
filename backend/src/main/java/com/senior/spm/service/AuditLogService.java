package com.senior.spm.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.entity.AuditLog;
import com.senior.spm.entity.AuditLog.Outcome;
import com.senior.spm.entity.AuditLog.UserType;
import com.senior.spm.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Persists an audit event in its own transaction so the record is committed
     * even when the calling transaction rolls back (e.g. failed login attempt).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, UserType userType, String action, Outcome outcome, String ipAddress) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setUserType(userType);
        log.setAction(action);
        log.setOutcome(outcome);
        log.setIpAddress(ipAddress);
        log.setOccurredAt(LocalDateTime.now());
        auditLogRepository.save(log);
    }
}
