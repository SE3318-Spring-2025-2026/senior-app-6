package com.senior.spm.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.senior.spm.controller.request.SubmitGradesRequest;
import com.senior.spm.controller.response.RubricGradeSubmitResponse;
import com.senior.spm.service.RubricGradingService;
import com.senior.spm.service.RubricGradingService.RubricGradingResult;
import com.senior.spm.util.SecurityUtils;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
@Validated
public class GradingController {

    private final RubricGradingService rubricGradingService;

    @PostMapping("/{submissionId}/grade")
    public ResponseEntity<RubricGradeSubmitResponse> submitGrades(
            @PathVariable UUID submissionId,
            @Valid @RequestBody SubmitGradesRequest request,
            Authentication auth) {

        UUID reviewerId = SecurityUtils.extractPrincipalUUID(auth);

        RubricGradingResult result = rubricGradingService.submitGrades(
                submissionId, reviewerId, request.getGrades());

        RubricGradeSubmitResponse response = new RubricGradeSubmitResponse(
                result.submissionId(),
                result.reviewerId(),
                result.baseDeliverableGrade());

        HttpStatus status = result.isFirstGrade() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}
