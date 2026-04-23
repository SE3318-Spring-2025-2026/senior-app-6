package com.senior.spm.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.senior.spm.service.dto.CommitteeAssignmentNotificationPayload;

@Component
public class LoggingCommitteeNotificationGateway implements CommitteeNotificationGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingCommitteeNotificationGateway.class);

    @Override
    public void sendAssignmentNotifications(List<CommitteeAssignmentNotificationPayload> notifications) {
        for (CommitteeAssignmentNotificationPayload notification : notifications) {
            log.info(
                    "Committee assignment notification => committeeId={}, professorId={}, professorMail={}, role={}, assignedGroups={}",
                    notification.committeeId(),
                    notification.professorId(),
                    notification.professorMail(),
                    notification.role(),
                    notification.assignedGroups());
        }
    }
}