package com.senior.spm.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.CreateDeliverableSubmissionRequest;
import com.senior.spm.controller.response.DeliverableSubmissionResponse;
import com.senior.spm.controller.response.DeliverableWithStatusResponse;
import com.senior.spm.service.DeliverableSubmissionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/deliverables")
@RequiredArgsConstructor
public class DeliverableController {

    private final DeliverableSubmissionService deliverableSubmissionService;

    /**
     * Submit the markdown document for a deliverable on behalf of the team leader.
     *
     * <p>Auth: Student JWT — requester must be the {@code TEAM_LEADER} of a group
     * that is assigned to a committee for the target deliverable.
     *
     * @param deliverableId UUID of the target deliverable (path)
     * @param request {@link CreateDeliverableSubmissionRequest} containing the markdown body
     * @return 201 with {@link DeliverableSubmissionResponse} containing submission metadata
     *
     * Error responses:
     * - 400: deliverable submission deadline has passed, or the group is not assigned to a committee for this deliverable
     * - 403: requester is not the TEAM_LEADER
     * - 404: deliverable not found
     */
    @GetMapping
    public ResponseEntity<List<DeliverableWithStatusResponse>> listDeliverables() {
        UUID requesterUUID = extractStudentUUIDFromJWT();
        return ResponseEntity.ok(deliverableSubmissionService.listDeliverablesForStudent(requesterUUID));
    }

    @PostMapping("/{deliverableId}/submissions")
    public ResponseEntity<DeliverableSubmissionResponse> createSubmission(
            @PathVariable UUID deliverableId,
            @Valid @RequestBody CreateDeliverableSubmissionRequest request) {
        UUID requesterUUID = extractStudentUUIDFromJWT();
        DeliverableSubmissionResponse response = deliverableSubmissionService.createSubmission(
                deliverableId,
                requesterUUID,
                request.getMarkdownContent());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private UUID extractStudentUUIDFromJWT() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString((String) authentication.getPrincipal());
    }
}
