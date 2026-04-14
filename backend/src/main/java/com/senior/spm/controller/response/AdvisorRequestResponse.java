package com.senior.spm.controller.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.senior.spm.entity.AdvisorRequest.RequestStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for advisor-request operations on the student-facing endpoints.
 *
 * <p>Shared across three endpoints; nullable fields are omitted by the service layer
 * depending on the operation:
 * <ul>
 *   <li>{@code POST /api/groups/{groupId}/advisor-request} (201) — all fields populated except
 *       {@code advisorName} and {@code respondedAt}.</li>
 *   <li>{@code GET /api/groups/{groupId}/advisor-request} (200) — all fields populated;
 *       {@code groupId} omitted (redundant with path param).</li>
 *   <li>{@code DELETE /api/groups/{groupId}/advisor-request} (200) — only {@code requestId} and
 *       {@code status} are populated (status = {@code CANCELLED}).</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvisorRequestResponse {

    private UUID requestId;
    /** Populated on POST response; omitted on GET (path already implies the group). */
    private UUID groupId;
    private UUID advisorId;
    /** Advisor display name; populated on GET only (requires join to StaffUser). */
    private String advisorName;
    private RequestStatus status;
    private LocalDateTime sentAt;
    /** Null while the request is still PENDING or CANCELLED. */
    private LocalDateTime respondedAt;
}
