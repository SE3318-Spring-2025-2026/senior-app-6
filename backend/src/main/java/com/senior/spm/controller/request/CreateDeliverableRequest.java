package com.senior.spm.controller.request;

import java.time.LocalDateTime;

import com.senior.spm.entity.Deliverable.DeliverableType;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDeliverableRequest {

    @NotBlank(message = "Deliverable name is required")
    private String name;

    @NotNull(message = "Deliverable type is required")
    @Enumerated(EnumType.STRING)
    private DeliverableType type;

    @NotNull(message = "Submission deadline is required")
    private LocalDateTime submissionDeadline;

    @NotNull(message = "Review deadline is required")
    private LocalDateTime reviewDeadline;

}
