package com.senior.spm.controller.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditLogResponse(UUID id, UUID userId, String userType,
        String category, String action, String outcome,
        String ipAddress, LocalDateTime occurredAt) {}
