package com.senior.spm.controller.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateSystemConfigRequest {

    private String activeTermId;

    @Min(1)
    private Integer maxTeamSize;
}
