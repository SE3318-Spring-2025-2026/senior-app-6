package com.senior.spm.service.dto;

import java.util.List;
import java.util.UUID;

import com.senior.spm.entity.CommitteeProfessor.ProfessorRole;

public record CommitteeAssignmentNotificationPayload(
        UUID committeeId,
        UUID professorId,
        String professorMail,
        ProfessorRole role,
        List<CommitteeAssignedGroupPayload> assignedGroups) {
}