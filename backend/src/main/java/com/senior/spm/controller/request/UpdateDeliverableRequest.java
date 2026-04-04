package com.senior.spm.controller.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.senior.spm.entity.Deliverable.DeliverableType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDeliverableRequest {

    private String name;

    private DeliverableType type;

    private LocalDateTime submissionDeadline;

    private LocalDateTime reviewDeadline;

    private BigDecimal weight;

}
