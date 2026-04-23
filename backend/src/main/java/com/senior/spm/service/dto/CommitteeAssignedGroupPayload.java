package com.senior.spm.service.dto;

import java.util.UUID;

public record CommitteeAssignedGroupPayload(
        UUID groupId,
        String groupName) {
}