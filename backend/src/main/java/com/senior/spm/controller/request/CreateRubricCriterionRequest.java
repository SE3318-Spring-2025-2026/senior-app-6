package com.senior.spm.controller.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateRubricCriterionRequest {

    @NotBlank(message = "criterionName is required.")
    private String criterionName;

    @NotBlank(message = "gradingType is required.")
    private String gradingType;

    @NotNull(message = "weight is required.")
    @DecimalMin(value = "0.00", inclusive = false, message = "weight must be greater than 0.")
    private BigDecimal weight;
}