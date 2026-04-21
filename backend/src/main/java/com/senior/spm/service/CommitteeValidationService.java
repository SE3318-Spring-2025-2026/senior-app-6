package com.senior.spm.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.senior.spm.exception.ConflictException;
import com.senior.spm.repository.CommitteeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommitteeValidationService {

    private final CommitteeRepository committeeRepository;

    /**
     * Validates that a professor is not already assigned to another committee for the same deliverable.
     *
     * @param professorId the ID of the professor
     * @param deliverableId the ID of the deliverable
     * @throws ConflictException if the professor is already assigned to another committee for this deliverable
     */
    public void validateProfessorNotAssignedToOtherCommittee(UUID professorId, UUID deliverableId) {
        if (committeeRepository.existsByProfessorIdAndDeliverableId(professorId, deliverableId)) {
            throw new ConflictException("Professor is already assigned to another committee for this deliverable");
        }
    }

    /**
     * Validates that a student group is not already assigned to another committee for the same deliverable.
     *
     * @param groupId the ID of the student group
     * @param deliverableId the ID of the deliverable
     * @throws ConflictException if the group is already assigned to another committee for this deliverable
     */
    public void validateGroupNotAssignedToOtherCommittee(UUID groupId, UUID deliverableId) {
        if (committeeRepository.existsByGroupIdAndDeliverableId(groupId, deliverableId)) {
            throw new ConflictException("Group is already assigned to another committee for this deliverable");
        }
    }
}