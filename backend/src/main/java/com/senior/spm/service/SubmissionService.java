package com.senior.spm.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.request.RubricMappingRequest;
import com.senior.spm.entity.DeliverableSubmission;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.RubricCriterion;
import com.senior.spm.entity.RubricMapping;
import com.senior.spm.exception.BusinessRuleException;
import com.senior.spm.exception.ForbiddenException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.DeliverableSubmissionRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.RubricCriterionRepository;
import com.senior.spm.repository.RubricMappingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final DeliverableSubmissionRepository submissionRepository;
    private final RubricCriterionRepository rubricCriterionRepository;
    private final RubricMappingRepository rubricMappingRepository;
    private final GroupMembershipRepository groupMembershipRepository;

    @Transactional
    public void saveRubricMappings(UUID submissionId, UUID requesterUUID,
            List<RubricMappingRequest> mappings) {
        DeliverableSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found"));

        ProjectGroup group = submission.getGroup();
        groupMembershipRepository.findByGroupIdAndStudentId(group.getId(), requesterUUID)
                .filter(membership -> membership.getRole() == com.senior.spm.entity.GroupMembership.MemberRole.TEAM_LEADER)
                .orElseThrow(() -> new ForbiddenException("Only the Team Leader can modify rubric mappings"));

        List<RubricCriterion> criteria = rubricCriterionRepository.findByDeliverableId(submission.getDeliverable().getId());
        Map<UUID, RubricCriterion> criteriaById = criteria.stream()
                .collect(Collectors.toMap(RubricCriterion::getId, criterion -> criterion));

        Set<UUID> requestedCriterionIds = mappings.stream()
                .map(RubricMappingRequest::getCriterionId)
                .collect(Collectors.toSet());

        Set<UUID> invalidCriteria = requestedCriterionIds.stream()
                .filter(id -> !criteriaById.containsKey(id))
                .collect(Collectors.toSet());

        if (!invalidCriteria.isEmpty()) {
            throw new BusinessRuleException("Invalid rubric criterion ID(s): " + invalidCriteria);
        }

        rubricMappingRepository.deleteBySubmissionId(submissionId);

        List<RubricMapping> mappingEntities = mappings.stream().map(request -> {
            RubricMapping mapping = new RubricMapping();
            mapping.setSubmission(submission);
            mapping.setSectionKey(request.getSectionKey());
            mapping.setRubricCriterion(criteriaById.get(request.getCriterionId()));
            mapping.setMappedAt(LocalDateTime.now(ZoneId.of("UTC")));
            mapping.setSectionStart(0);
            mapping.setSectionEnd(0);
            return mapping;
        }).collect(Collectors.toList());

        if (!mappingEntities.isEmpty()) {
            rubricMappingRepository.saveAll(mappingEntities);
        }
    }
}
