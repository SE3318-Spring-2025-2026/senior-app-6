package com.senior.spm.controller.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.senior.spm.entity.ScrumGrade.ScrumGradeValue;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScrumGradeResponse {

    private UUID gradeId;
    private UUID groupId;
    private UUID sprintId;

    @JsonProperty("pointA_grade")
    private ScrumGradeValue pointAGrade;

    @JsonProperty("pointB_grade")
    private ScrumGradeValue pointBGrade;

    private UUID advisorId;
    private LocalDateTime gradedAt;

    // null on first submission (201); populated on update (200)
    private LocalDateTime updatedAt;
}
