package com.senior.spm.controller.response;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.senior.spm.entity.ScrumGrade.ScrumGradeValue;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdvisorGroupSprintSummaryResponse {

    private UUID groupId;
    private String groupName;
    private int totalIssues;
    private long mergedPRs;
    private long aiPassCount;
    private long aiWarnCount;
    private long aiFailCount;
    private long aiPendingCount;
    private long aiSkippedCount;
    private boolean gradeSubmitted;

    @JsonProperty("pointA_grade")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ScrumGradeValue pointAGrade;

    @JsonProperty("pointB_grade")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ScrumGradeValue pointBGrade;

    private List<PerStudentSummaryResponse> perStudentSummary;
}
