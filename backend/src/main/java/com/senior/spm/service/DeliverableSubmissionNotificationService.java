package com.senior.spm.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.senior.spm.entity.StaffUser;
import com.senior.spm.repository.CommitteeRepository;
import com.senior.spm.service.dto.DeliverableSubmissionNotificationPayload;
import com.senior.spm.service.event.DeliverableSubmissionCreatedEvent;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeliverableSubmissionNotificationService {

    private static final Logger log = LoggerFactory.getLogger(DeliverableSubmissionNotificationService.class);

    private final CommitteeRepository committeeRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDeliverableSubmissionCreated(DeliverableSubmissionCreatedEvent event) {
        List<StaffUser> assignedProfessors =
                committeeRepository.findAssignedProfessorsByGroupIdAndDeliverableId(
                        event.getGroupId(),
                        event.getDeliverableId()
                );

        assignedProfessors.stream()
                .map(professor -> new DeliverableSubmissionNotificationPayload(
                        event.getSubmissionId(),
                        event.getGroupId(),
                        event.getDeliverableId(),
                        professor.getId(),
                        professor.getMail()
                ))
                .forEach(this::dispatchNotification);
    }

    private void dispatchNotification(DeliverableSubmissionNotificationPayload payload) {
        log.info(
                "Deliverable submission notification dispatched. submissionId={}, groupId={}, deliverableId={}, professorId={}, professorEmail={}",
                payload.getSubmissionId(),
                payload.getGroupId(),
                payload.getDeliverableId(),
                payload.getProfessorId(),
                payload.getProfessorEmail()
        );
    }
}