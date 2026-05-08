package com.senior.spm.repository;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.senior.spm.entity.AuditLog;
import com.senior.spm.entity.AuditLog.Category;
import com.senior.spm.entity.AuditLog.Outcome;
import com.senior.spm.entity.AuditLog.UserType;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:userType IS NULL OR a.userType = :userType)
              AND (:category IS NULL OR a.category = :category)
              AND (:outcome  IS NULL OR a.outcome  = :outcome)
              AND (:userId   IS NULL OR a.userId   = :userId)
              AND (:from     IS NULL OR a.occurredAt >= :from)
              AND (:to       IS NULL OR a.occurredAt <= :to)
            ORDER BY a.occurredAt DESC
            """)
    Page<AuditLog> search(
            @Param("userType") UserType   userType,
            @Param("category") Category   category,
            @Param("outcome")  Outcome    outcome,
            @Param("userId")   UUID       userId,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to,
            Pageable pageable
    );
}
