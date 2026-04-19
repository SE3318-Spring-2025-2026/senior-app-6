package com.senior.spm.service;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitteeAssignmentNotificationPayload {
    private UUID committeeId;
    private List<UUID> professorIds;
    private List<AssignedGroupPayload> assignedGroups;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignedGroupPayload {
        private UUID groupId;
        private String groupName;
    }
}