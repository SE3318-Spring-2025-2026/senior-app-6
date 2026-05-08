package com.senior.spm.controller.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateAdvisorCapacityRequest {

    @NotNull
    @Min(1)
    @Max(20)
    private Integer capacity;
}
