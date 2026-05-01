package com.senior.spm.controller.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubmissionCommentResponse {

    private UUID id;
    private UUID submissionId;
    private UUID reviewerId;
    private String reviewerEmail;
    private String commentText;
    private String sectionReference;
    private LocalDateTime createdAt;
}