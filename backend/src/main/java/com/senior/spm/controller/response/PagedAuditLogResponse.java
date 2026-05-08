package com.senior.spm.controller.response;

import java.util.List;

public record PagedAuditLogResponse(List<AuditLogResponse> content,
        int page, int size, long totalElements, int totalPages) {}
