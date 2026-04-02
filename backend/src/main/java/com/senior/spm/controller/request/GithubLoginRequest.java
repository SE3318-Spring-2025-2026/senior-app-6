package com.senior.spm.controller.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GithubLoginRequest {

    @NotBlank
    private String accessToken;

    @NotBlank
    private String username;

    @NotBlank
    private String email;

    @NotBlank
    private String studentId;
}
