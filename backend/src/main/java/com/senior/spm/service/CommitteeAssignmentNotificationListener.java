package com.senior.spm.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.senior.spm.service.event.CommitteeAssignmentNotificationsEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CommitteeAssignmentNotificationListener {

    private final CommitteeNotificationGateway committeeNotificationGateway;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitteeAssignmentReady(CommitteeAssignmentNotificationsEvent event) {
        if (event.notifications() == null || event.notifications().isEmpty()) {
            return;
        }

        committeeNotificationGateway.sendAssignmentNotifications(event.notifications());
    }
}