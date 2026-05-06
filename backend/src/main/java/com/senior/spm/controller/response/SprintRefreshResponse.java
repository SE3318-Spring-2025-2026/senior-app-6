package com.senior.spm.controller.response;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body for {@code POST /api/coordinator/sprints/{sprintId}/refresh}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SprintRefreshResponse {

    private UUID sprintId;
    private int groupsProcessed;
    private int issuesFetched;
    private int aiValidationsRun;
    private LocalDateTime triggeredAt;
}
