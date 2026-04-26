package com.senior.spm.controller.response;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body for {@code GET /api/coordinator/sprints/{sprintId}/overview}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SprintOverviewResponse {

    private UUID sprintId;
    private List<SprintGroupOverview> groups;
}
