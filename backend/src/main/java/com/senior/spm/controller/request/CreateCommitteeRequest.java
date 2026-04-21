package com.senior.spm.controller.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateCommitteeRequest {

    @NotBlank
    private String committeeName;

    @NotBlank
    private String termId;

    @NotNull
    private UUID deliverableId;
}
