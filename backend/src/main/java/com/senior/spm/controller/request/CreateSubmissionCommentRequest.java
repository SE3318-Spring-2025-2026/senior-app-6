package com.senior.spm.controller.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSubmissionCommentRequest {

    @NotBlank(message = "commentText is required")
    private String commentText;
}