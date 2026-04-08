// TODO: Issue #45 — [Backend] Invitation Lifecycle Services & Controller
package com.senior.spm.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendInvitationRequest {

    @NotBlank(message = "Target student ID is required")
    private String targetStudentId;
}
