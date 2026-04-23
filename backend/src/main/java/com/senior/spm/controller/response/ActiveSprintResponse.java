package com.senior.spm.controller.response;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActiveSprintResponse {

    private UUID sprintId;
    private LocalDate startDate;
    private LocalDate endDate;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer storyPointTarget;
    private long daysRemaining;
}
