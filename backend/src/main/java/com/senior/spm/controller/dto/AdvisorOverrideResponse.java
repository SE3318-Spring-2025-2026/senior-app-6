package com.senior.spm.controller.dto;

import java.util.UUID;

import com.senior.spm.entity.ProjectGroup.GroupStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body for {@code PATCH /api/coordinator/groups/{groupId}/advisor}.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code groupId} — UUID of the affected group</li>
 *   <li>{@code status} — updated group status ({@code ADVISOR_ASSIGNED} on ASSIGN, {@code TOOLS_BOUND} on REMOVE)</li>
 *   <li>{@code advisorId} — UUID of the assigned advisor, or {@code null} after a REMOVE action</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvisorOverrideResponse {

    private UUID groupId;
    private GroupStatus status;
    private UUID advisorId;
}
