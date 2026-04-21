package com.senior.spm.controller.request;

import java.util.UUID;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request body for {@code PATCH /api/coordinator/groups/{groupId}/advisor}.
 *
 * <p>Coordinator bypass — skips both the schedule-window check and the advisor capacity limit.
 *
 * <p>Validation rules:
 * <ul>
 *   <li>{@code action} must be {@code "ASSIGN"} or {@code "REMOVE"}.</li>
 *   <li>{@code advisorId} is required when {@code action = "ASSIGN"}; validated at the service
 *       layer (returns 400 if missing). It is intentionally {@code @Nullable} here so that the
 *       DTO deserializes cleanly for REMOVE requests without requiring the field.</li>
 * </ul>
 */
@Data
public class AdvisorOverrideRequest {

    @NotBlank(message = "action is required")
    @Pattern(regexp = "ASSIGN|REMOVE", message = "action must be ASSIGN or REMOVE")
    private String action;

    /** Required for ASSIGN; ignored for REMOVE. Validated at service layer. */
    @Nullable
    private UUID advisorId;
}
