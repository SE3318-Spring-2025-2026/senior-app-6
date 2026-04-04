package com.senior.spm.controller.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateDeliverableWeightRequest {

    @NotNull(message = "weightPercentage is required.")
    @DecimalMin(value = "0.00", inclusive = false, message = "weightPercentage must be greater than 0.")
    @DecimalMax(value = "100.00", inclusive = true, message = "weightPercentage cannot exceed 100.")
    private BigDecimal weightPercentage;
}