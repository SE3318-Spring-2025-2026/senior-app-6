package com.senior.spm.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.CreateSubmissionCommentRequest;
import com.senior.spm.controller.response.SubmissionCommentResponse;
import com.senior.spm.service.SubmissionCommentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionCommentController {

    private final SubmissionCommentService submissionCommentService;

    @PostMapping("/{id}/comments")
    public ResponseEntity<SubmissionCommentResponse> createComment(
            @PathVariable UUID id,
            @Valid @RequestBody CreateSubmissionCommentRequest request) {

        UUID reviewerId = extractReviewerIdFromJwt();

        SubmissionCommentResponse response = submissionCommentService.createComment(
                id,
                reviewerId,
                request.getCommentText(),
                request.getSectionReference()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private UUID extractReviewerIdFromJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString((String) authentication.getPrincipal());
    }
}