package com.senior.spm.controller.response;

import com.senior.spm.entity.AiValidationResult;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrackingIssueResponse {

    private String issueKey;
    private String assigneeGithubUsername;
    private Integer storyPoints;
    private Long prNumber;
    private Boolean prMerged;
    private AiValidationResult aiPrResult;
    private AiValidationResult aiDiffResult;
}
