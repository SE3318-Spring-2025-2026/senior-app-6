package com.senior.spm.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.RubricMappingRequest;
import com.senior.spm.service.SubmissionService;
import com.senior.spm.util.SecurityUtils;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
@Validated
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping("/{submissionId}/rubric-mappings")
    public ResponseEntity<Void> saveRubricMappings(
            @PathVariable UUID submissionId,
            @Valid @RequestBody List<@Valid RubricMappingRequest> mappings,
            Authentication auth
    ) {
        UUID requesterUUID = SecurityUtils.extractPrincipalUUID(auth);
        submissionService.saveRubricMappings(submissionId, requesterUUID, mappings);
        return ResponseEntity.ok().build();
    }
}
