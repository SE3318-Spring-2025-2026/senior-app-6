package com.senior.spm.controller.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank
    private String mail;
    @NotBlank
    private String password;
}
