package com.senior.spm.controller.response;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.senior.spm.entity.ScrumGrade.ScrumGradeValue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-group summary entry within {@link SprintOverviewResponse}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SprintGroupOverview {

    private UUID groupId;
    private String groupName;

    /** Advisor email; null for TOOLS_BOUND groups without an assigned advisor. Omitted from JSON when null. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String advisorEmail;

    private int totalIssues;
    private int mergedPRs;
    private int aiPassCount;
    private int aiWarnCount;
    private int aiFailCount;
    private int aiPendingCount;
    private int aiSkippedCount;

    private boolean gradeSubmitted;

    /** Always present in JSON — null when no grade has been submitted yet. */
    @JsonProperty("pointA_grade")
    private ScrumGradeValue pointAGrade;

    /** Always present in JSON — null when no grade has been submitted yet. */
    @JsonProperty("pointB_grade")
    private ScrumGradeValue pointBGrade;
}
