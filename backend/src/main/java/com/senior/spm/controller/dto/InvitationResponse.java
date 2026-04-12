// TODO: Issue #45 — [Backend] Invitation Lifecycle Services & Controller
package com.senior.spm.controller.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * Response DTO used by invitation lifecycle endpoints.
 *
 * <p>The same DTO supports group-owned invitation views and student inbox
 * views. Fields that do not apply to a specific endpoint are omitted from JSON
 * through {@link JsonInclude.Include#NON_NULL}.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class InvitationResponse implements InvitationActionResponse {

    private UUID invitationId;
    private UUID groupId;
    private String targetStudentId;
    private String status;
    private LocalDateTime sentAt;
    private LocalDateTime respondedAt;
    private String groupName;
    private String teamLeaderStudentId;
}
