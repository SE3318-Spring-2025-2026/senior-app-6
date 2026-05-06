package com.senior.spm.controller.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateDeliverableSubmissionRequest {

    @NotBlank
    private String markdownContent;
}
