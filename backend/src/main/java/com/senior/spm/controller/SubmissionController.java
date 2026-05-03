package com.senior.spm.controller;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.UpdateDeliverableSubmissionRequest;
import com.senior.spm.controller.response.DeliverableSubmissionDetailResponse;
import com.senior.spm.controller.response.DeliverableSubmissionResponse;
import com.senior.spm.service.DeliverableSubmissionService;
import com.senior.spm.controller.request.RubricMappingRequest;
import com.senior.spm.service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
@Validated
public class SubmissionController {

    private final DeliverableSubmissionService deliverableSubmissionService;
    private final SubmissionService submissionService;

    @PutMapping("/{submissionId}")
    public ResponseEntity<DeliverableSubmissionResponse> updateSubmission(
            @PathVariable UUID submissionId,
            @Valid @RequestBody UpdateDeliverableSubmissionRequest request) {
        UUID requesterUUID = extractPrincipalUUID();
        DeliverableSubmissionResponse response = deliverableSubmissionService.updateSubmission(
                submissionId,
                requesterUUID,
                request.getMarkdownContent());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{submissionId}")
    public ResponseEntity<DeliverableSubmissionDetailResponse> getSubmission(
            @PathVariable UUID submissionId) {
        UUID requesterUUID = extractPrincipalUUID();
        String role = extractRole();
        DeliverableSubmissionDetailResponse response = deliverableSubmissionService.getSubmission(
                submissionId, requesterUUID, role);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{submissionId}/rubric-mappings")
    public ResponseEntity<Void> saveRubricMappings(
            @PathVariable UUID submissionId,
            @Valid @RequestBody List<@Valid RubricMappingRequest> mappings
    ) {
        UUID requesterUUID = extractPrincipalUUID();
        submissionService.saveRubricMappings(submissionId, requesterUUID, mappings);
        return ResponseEntity.ok().build();
    }

    private UUID extractPrincipalUUID() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString((String) authentication.getPrincipal());
    }

    private String extractRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        GrantedAuthority authority = authentication.getAuthorities().iterator().next();
        String name = authority.getAuthority();
        return name.startsWith("ROLE_") ? name.substring(5) : name;
    }
}
