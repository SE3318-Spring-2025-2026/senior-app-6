package com.senior.spm.controller.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCommitteeRequest {

    @NotBlank(message = "Committee name is required")
    private String committeeName;

    @NotBlank(message = "Term id is required")
    private String termId;
}