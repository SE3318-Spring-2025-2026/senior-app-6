package com.senior.spm.controller.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SprintTrackingResponse {

    private UUID groupId;
    private UUID sprintId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalDateTime fetchedAt;

    private List<TrackingIssueResponse> issues;

    // null for student view; populated for advisor view
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<PerStudentSummaryResponse> perStudentSummary;
}
