package com.senior.spm.service.event;

import java.util.List;
import java.util.UUID;

import com.senior.spm.service.dto.CommitteeAssignmentNotificationPayload;

public record CommitteeAssignmentNotificationsEvent(
        UUID committeeId,
        List<CommitteeAssignmentNotificationPayload> notifications) {
}