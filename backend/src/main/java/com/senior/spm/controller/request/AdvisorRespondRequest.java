package com.senior.spm.controller.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for {@code PATCH /api/advisor/requests/{requestId}/respond}.
 * Sent by an authenticated Professor to accept or reject a pending advisor request.
 */
@Data
public class AdvisorRespondRequest {

    @NotNull(message = "accept field is required")
    private Boolean accept;
}
