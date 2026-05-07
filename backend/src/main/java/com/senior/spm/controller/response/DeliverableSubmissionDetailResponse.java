package com.senior.spm.controller.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeliverableSubmissionDetailResponse {

    private UUID submissionId;
    private UUID groupId;
    private UUID deliverableId;
    private String markdownContent;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
    private int revisionNumber;
    private boolean isRevision;
}
