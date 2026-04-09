// TODO: Issue #45 — [Backend] Invitation Lifecycle Services & Controller
package com.senior.spm.controller.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Data;

@Data
public class InvitationResponse {

    private UUID invitationId;
    private UUID groupId;
    private String targetStudentId;
    private String status;
    private LocalDateTime sentAt;
}
