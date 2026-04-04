package com.senior.spm.controller.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateSprintDeliverableMappingRequest {

    @NotNull(message = "deliverableId is required.")
    private UUID deliverableId;

    @NotNull(message = "contributionPercentage is required.")
    @DecimalMin(value = "0.00", inclusive = false, message = "contributionPercentage must be greater than 0.")
    @DecimalMax(value = "100.00", inclusive = true, message = "contributionPercentage cannot be greater than 100.")
    private BigDecimal contributionPercentage;
}