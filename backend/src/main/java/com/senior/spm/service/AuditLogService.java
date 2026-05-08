package com.senior.spm.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.entity.AuditLog;
import com.senior.spm.entity.AuditLog.Category;
import com.senior.spm.entity.AuditLog.Outcome;
import com.senior.spm.entity.AuditLog.UserType;
import com.senior.spm.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Persists an audit event in its own transaction so the record is committed
     * even when the calling transaction rolls back (e.g. failed login attempt).
     * Exceptions from the audit write are swallowed so a DB hiccup never aborts
     * the calling business operation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, UserType userType, String action, Category category, Outcome outcome, String ipAddress) {
        try {
            AuditLog entry = new AuditLog();
            entry.setUserId(userId);
            entry.setUserType(userType);
            entry.setAction(action);
            entry.setCategory(category);
            entry.setOutcome(outcome);
            entry.setIpAddress(ipAddress);
            entry.setOccurredAt(LocalDateTime.now());
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Audit write failed — action={} userId={} outcome={}", action, userId, outcome, e);
        }
    }
}
