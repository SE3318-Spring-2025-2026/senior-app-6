package com.senior.spm.controller.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSprintTargetRequest {

    @NotNull(message = "Story point target is required")
    @Positive(message = "Story point target must be positive")
    private Integer targetStoryPoint;
}
