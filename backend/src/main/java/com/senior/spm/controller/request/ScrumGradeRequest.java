package com.senior.spm.controller.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.senior.spm.entity.ScrumGrade.ScrumGradeValue;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ScrumGradeRequest {

    @NotNull
    @JsonProperty("pointA_grade")
    private ScrumGradeValue pointAGrade;

    @NotNull
    @JsonProperty("pointB_grade")
    private ScrumGradeValue pointBGrade;
}
