package com.senior.spm.controller.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommitteeSubmissionSummaryResponse {

    private UUID submissionId;
    private UUID groupId;
    private String groupName;
    private UUID deliverableId;
    private String deliverableName;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
    private int revisionNumber;
    private boolean isRevision;
    private long commentCount;
}
