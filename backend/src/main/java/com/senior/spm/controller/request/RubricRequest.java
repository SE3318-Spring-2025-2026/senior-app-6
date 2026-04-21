package com.senior.spm.controller.request;

import java.math.BigDecimal;
import java.util.List;

import org.hibernate.validator.constraints.Range;

import com.senior.spm.entity.RubricCriterion;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RubricRequest {

    @NotEmpty
    List<Criterion> criteria;

    @Data
    @AllArgsConstructor
    public static class Criterion {

        @NotBlank
        private String criterionName;

        @NotNull
        @Enumerated(EnumType.STRING)
        private RubricCriterion.GradingType gradingType;

        @NotNull
        @Range(min = 0, max = 100, message = "Weight percentage must be between 0 and 100")
        private BigDecimal weight;
    }
}
