package com.senior.spm.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.response.SubmissionCommentResponse;
import com.senior.spm.service.SubmissionService;

import lombok.RequiredArgsConstructor;

/**
 * REST controller for submission-related endpoints.
 */
@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
@Validated
public class SubmissionController {

    private final SubmissionService submissionService;

    /**
     * Returns comments for the specified submission.
     *
     * <p>
     * Auth: any authenticated student or staff user with access to the submission.
     *
     * @param submissionId UUID of the submission
     * @param auth current authentication token
     * @return 200 with list of submission comments
     */
    @GetMapping("/{submissionId}/comments")
    public ResponseEntity<List<SubmissionCommentResponse>> getSubmissionComments(
            @PathVariable UUID submissionId,
            Authentication auth) {
        List<SubmissionCommentResponse> comments = submissionService.getSubmissionComments(submissionId, auth);
        return ResponseEntity.ok(comments);
    }
}
