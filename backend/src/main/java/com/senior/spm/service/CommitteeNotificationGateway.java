package com.senior.spm.service;

import java.util.List;

import com.senior.spm.service.dto.CommitteeAssignmentNotificationPayload;

public interface CommitteeNotificationGateway {
    void sendAssignmentNotifications(List<CommitteeAssignmentNotificationPayload> notifications);
}