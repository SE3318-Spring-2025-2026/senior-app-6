package com.senior.spm.controller.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RubricMappingRequest {

    @NotNull(message = "criterionId is required")
    private UUID criterionId;

    @NotBlank(message = "sectionKey is required")
    private String sectionKey;
}
