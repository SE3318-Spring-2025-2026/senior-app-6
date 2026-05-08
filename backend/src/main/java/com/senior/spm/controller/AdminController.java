package com.senior.spm.controller;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.LlmConfigRequest;
import com.senior.spm.controller.request.RegisterProfessorRequest;
import com.senior.spm.controller.response.AuditLogResponse;
import com.senior.spm.controller.response.ErrorMessage;
import com.senior.spm.controller.response.LlmConfigResponse;
import com.senior.spm.controller.response.PagedAuditLogResponse;
import com.senior.spm.entity.AuditLog;
import com.senior.spm.exception.AlreadyExistsException;
import com.senior.spm.service.AuditLogService;
import com.senior.spm.service.LlmConfigService;
import com.senior.spm.service.StaffUserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final StaffUserService staffUserService;
    private final LlmConfigService llmConfigService;
    private final AuditLogService auditLogService;

    public AdminController(StaffUserService staffUserService,
                           LlmConfigService llmConfigService,
                           AuditLogService auditLogService) {
        this.staffUserService = staffUserService;
        this.llmConfigService = llmConfigService;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/register-professor")
    public ResponseEntity<?> registerProfessor(@Valid @RequestBody RegisterProfessorRequest request) {
        var mail = request.getMail();

        try {
            var token = staffUserService.registerProfessor(mail,request.getCapacity());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("resetToken", token));
        } catch (AlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorMessage("Professor with same mail already exists"));
        }
    }

    @GetMapping("/llm-config")
    public ResponseEntity<LlmConfigResponse> getLlmConfig() {
        return ResponseEntity.ok(llmConfigService.getLlmConfig());
    }

    @PutMapping("/llm-config")
    public ResponseEntity<Map<String, String>> updateLlmConfig(@Valid @RequestBody LlmConfigRequest request) {
        llmConfigService.updateLlmKey(request.getApiKey());
        return ResponseEntity.ok(Map.of("message", "LLM API key updated successfully"));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<PagedAuditLogResponse> getAuditLogs(
            @RequestParam(required = false) AuditLog.UserType userType,
            @RequestParam(required = false) AuditLog.Category category,
            @RequestParam(required = false) AuditLog.Outcome outcome,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC)
                Pageable pageable) {

        if (pageable.getPageSize() > 100) {
            pageable = PageRequest.of(pageable.getPageNumber(), 100, pageable.getSort());
        }

        Page<AuditLogResponse> page = auditLogService.query(
                userType, category, outcome, userId, from, to, pageable);

        return ResponseEntity.ok(new PagedAuditLogResponse(
                page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages()));
    }
}
