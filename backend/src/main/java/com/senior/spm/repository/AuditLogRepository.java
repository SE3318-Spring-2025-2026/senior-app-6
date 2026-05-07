package com.senior.spm.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.senior.spm.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
