package com.senior.spm.controller.response;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.validator.constraints.Range;

import com.senior.spm.entity.RubricCriterion;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CriterionResponse {

    @NotNull
    private UUID id;

    @NotBlank
    private String criterionName;

    @NotNull
    @Enumerated(EnumType.STRING)
    private RubricCriterion.GradingType gradingType;

    @NotNull
    @Range(min = 0, max = 100, message = "Weight percentage must be between 0 and 100")
    private BigDecimal weight;
}
