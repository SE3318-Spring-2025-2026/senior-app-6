package com.senior.spm.controller.request;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class StudentUploadRequest {

    @NotNull
    @NotEmpty
    private List<@Pattern(regexp = "^[0-9]{11}$") String> studentIds;
}
