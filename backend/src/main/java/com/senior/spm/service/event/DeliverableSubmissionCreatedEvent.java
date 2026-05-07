package com.senior.spm.service.event;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DeliverableSubmissionCreatedEvent {

    private final UUID submissionId;
    private final UUID groupId;
    private final UUID deliverableId;
}