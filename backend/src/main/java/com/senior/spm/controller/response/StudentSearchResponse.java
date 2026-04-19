package com.senior.spm.controller.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class StudentSearchResponse {

    private String studentId;
    private String githubUsername;
}
