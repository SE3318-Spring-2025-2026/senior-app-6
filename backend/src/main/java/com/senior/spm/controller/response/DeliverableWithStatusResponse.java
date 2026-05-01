package com.senior.spm.controller.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.senior.spm.entity.Deliverable.DeliverableType;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeliverableWithStatusResponse {

    public enum SubmissionStatus {
        NOT_SUBMITTED, SUBMITTED
    }

    private UUID id;
    private String name;
    private DeliverableType type;
    private LocalDateTime submissionDeadline;
    private LocalDateTime reviewDeadline;
    private BigDecimal weight;
    private SubmissionStatus submissionStatus;
}
