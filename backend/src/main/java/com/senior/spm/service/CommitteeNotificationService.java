package com.senior.spm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CommitteeNotificationService {

    private static final Logger log = LoggerFactory.getLogger(CommitteeNotificationService.class);

    public void sendAssignmentNotification(CommitteeAssignmentNotificationPayload payload) {
        // Bu aşamada gerçek mail / queue / websocket altyapısı olmadığı için
        // notification payload'ını logluyoruz.
        // İleride bu method gerçek NotificationService entegrasyonuna çevrilebilir.
        log.info("Committee assignment notification triggered: committeeId={}, professorIds={}, assignedGroups={}",
                payload.getCommitteeId(),
                payload.getProfessorIds(),
                payload.getAssignedGroups());
    }
}