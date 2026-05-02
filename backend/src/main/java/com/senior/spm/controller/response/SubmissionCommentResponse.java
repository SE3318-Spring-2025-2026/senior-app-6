package com.senior.spm.controller.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SubmissionCommentResponse {

    private UUID commentId;
    private UUID submissionId;
    private UUID authorId;
    private String authorName;
    private String content;
    private String sectionReference;
    private LocalDateTime createdAt;
}