package com.senior.spm.controller.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterProfessorRequest {
    @NotBlank
    private String mail;

@Min(1)
@Max(20)
private Integer capacity; 
}
