package com.senior.spm.controller.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BindGithubRequest {

    @NotBlank(message = "GitHub organization name is required")
    private String githubOrgName;

    @NotBlank(message = "GitHub PAT is required")
    private String githubPat;
}
