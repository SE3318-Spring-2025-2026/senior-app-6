package com.senior.spm.controller.response;

import com.senior.spm.entity.SprintTrackingLog.AiValidationResult;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PerStudentSummaryResponse {

    private String assigneeGithubUsername;
    private int completedPoints;
    private AiValidationResult aiValidationStatus;
}
