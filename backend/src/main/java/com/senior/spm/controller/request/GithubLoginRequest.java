package com.senior.spm.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class GithubLoginRequest {

    @NotBlank
    private String code;

    @NotBlank
    @Pattern(regexp = "^[0-9]{11}$")
    private String studentId;
}
