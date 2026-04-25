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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SprintGroupOverview {

    private UUID groupId;
    private String groupName;

    /** Advisor's email; null for TOOLS_BOUND groups without an assigned advisor. */
    private String advisorName;

    private int totalIssues;
    private int mergedPRs;
    private int aiPassCount;
    private int aiWarnCount;
    private int aiFailCount;
    private int aiPendingCount;
    private int aiSkippedCount;

    private boolean gradeSubmitted;

    @JsonProperty("pointA_grade")
    private ScrumGradeValue pointAGrade;

    @JsonProperty("pointB_grade")
    private ScrumGradeValue pointBGrade;
}
