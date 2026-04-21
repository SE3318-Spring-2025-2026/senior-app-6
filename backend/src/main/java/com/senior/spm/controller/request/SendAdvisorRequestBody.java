package com.senior.spm.controller.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for {@code POST /api/groups/{groupId}/advisor-request}.
 * Sent by a student (must hold TEAM_LEADER role in the group) to initiate an advisor request.
 */
@Data
public class SendAdvisorRequestBody {

    @NotNull(message = "advisorId is required")
    private UUID advisorId;
}
