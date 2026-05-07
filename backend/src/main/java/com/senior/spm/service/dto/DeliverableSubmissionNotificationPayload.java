package com.senior.spm.service.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DeliverableSubmissionNotificationPayload {

    private final UUID submissionId;
    private final UUID groupId;
    private final UUID deliverableId;
    private final UUID professorId;
    private final String professorEmail;
}