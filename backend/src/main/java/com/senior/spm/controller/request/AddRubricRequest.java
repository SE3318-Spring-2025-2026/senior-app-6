package com.senior.spm.controller.request;

import java.math.BigDecimal;

import org.hibernate.validator.constraints.Range;

import com.senior.spm.entity.RubricCriterion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddRubricRequest {

    @NotBlank
    private String criterionName;

    @NotNull
    private RubricCriterion.GradingType gradingType;

    @NotNull
    @Range(min = 0, max = 100, message = "Weight percentage must be between 0 and 100")
    private BigDecimal weightPercentage;
}
