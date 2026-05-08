package com.senior.spm.controller.response;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SprintDeliverableMappingResponse {

    private UUID id;
    private UUID deliverableId;
    private String deliverableName;
    private BigDecimal contributionPercentage;
}
