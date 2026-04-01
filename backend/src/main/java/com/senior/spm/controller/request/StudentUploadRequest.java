package com.senior.spm.controller.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class StudentUploadRequest {

    @NotBlank
    private List<@Pattern(regexp = "^\\d{11}$") String> studentIds;
}
