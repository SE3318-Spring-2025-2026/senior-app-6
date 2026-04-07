package com.senior.spm.controller.request;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.validator.constraints.Range;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MapDeliverablesRequest {

    @NotNull
    private UUID deliverableId;
    @NotNull
    @Range(min = 0, max = 100, message = "Contribution percentage must be between 0 and 100")
    private BigDecimal contributionPercentage;
}
