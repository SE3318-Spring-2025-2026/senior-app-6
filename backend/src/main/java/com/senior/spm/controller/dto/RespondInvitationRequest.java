package com.senior.spm.controller.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RespondInvitationRequest {

    @NotNull(message = "accept field is required")
    private Boolean accept;
}
