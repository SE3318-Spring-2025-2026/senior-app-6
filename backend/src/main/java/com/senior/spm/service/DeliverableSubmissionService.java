package com.senior.spm.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.response.CommitteeSubmissionSummaryResponse;
import com.senior.spm.controller.response.DeliverableSubmissionDetailResponse;
import com.senior.spm.controller.response.DeliverableSubmissionResponse;
import com.senior.spm.controller.response.DeliverableWithStatusResponse;
import com.senior.spm.controller.response.DeliverableWithStatusResponse.SubmissionStatus;
import com.senior.spm.entity.Committee;
import com.senior.spm.entity.Deliverable;
import com.senior.spm.entity.DeliverableSubmission;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.GroupMembership.MemberRole;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.exception.BusinessRuleException;
import com.senior.spm.exception.ForbiddenException;
import com.senior.spm.exception.NotFoundException;
import com.senior.spm.repository.CommitteeRepository;
import com.senior.spm.repository.DeliverableRepository;
import com.senior.spm.repository.DeliverableSubmissionRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.SubmissionCommentRepository;
import com.senior.spm.service.event.DeliverableSubmissionCreatedEvent;

import lombok.RequiredArgsConstructor;

/**
 * Owns the deliverable submission flow (Process 6, Phase 6A).
 *
 * <p>Enforces RBAC (Team Leader only), committee assignment, and the
 * Coordinator-configured submission deadline. Revision numbers are derived from
 * the latest existing submission for the same (group, deliverable) pair.
 */
@Service
@RequiredArgsConstructor
public class DeliverableSubmissionService {

    private final DeliverableSubmissionRepository submissionRepository;
    private final DeliverableRepository deliverableRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final CommitteeRepository committeeRepository;
    private final SubmissionCommentRepository submissionCommentRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Create the initial (or revision) submission for a deliverable on behalf of the team leader.
     *
     * @param deliverableId UUID of the target deliverable
     * @param requesterUUID internal UUID of the authenticated student
     * @param markdownContent the markdown body of the submission
     * @return summary of the created submission
     * @throws NotFoundException if the deliverable does not exist
     * @throws ForbiddenException if the requester is not a TEAM_LEADER
     * @throws BusinessRuleException if the group is disbanded, not assigned to a committee for this deliverable, or the deadline has passed
     */
    @Transactional
    public DeliverableSubmissionResponse createSubmission(
            UUID deliverableId,
            UUID requesterUUID,
            String markdownContent) {

        Deliverable deliverable = deliverableRepository.findById(deliverableId)
                .orElseThrow(() -> new NotFoundException("Deliverable not found"));

        GroupMembership membership = groupMembershipRepository.findByStudentId(requesterUUID)
                .orElseThrow(() -> new ForbiddenException(
                        "Only the Team Leader can submit deliverables"));

        if (membership.getRole() != MemberRole.TEAM_LEADER) {
            throw new ForbiddenException("Only the Team Leader can submit deliverables");
        }

        ProjectGroup group = membership.getGroup();

        if (group.getStatus() == GroupStatus.DISBANDED) {
            throw new BusinessRuleException("Cannot submit deliverable for a disbanded group");
        }

        if (!committeeRepository.existsByGroupIdAndDeliverableId(group.getId(), deliverableId)) {
            throw new BusinessRuleException(
                    "Group is not assigned to a committee for this deliverable");
        }

        LocalDateTime now = nowUtc();
        if (now.isAfter(deliverable.getSubmissionDeadline())) {
            throw new BusinessRuleException("Submission deadline has passed");
        }

        DeliverableSubmission existing =
                submissionRepository.findFirstByGroupAndDeliverableOrderBySubmittedAtDesc(group, deliverable);

        DeliverableSubmission submission;
        if (existing == null) {
            submission = new DeliverableSubmission();
            submission.setGroup(group);
            submission.setDeliverable(deliverable);
            submission.setMarkdownContent(markdownContent);
            submission.setSubmittedAt(now);
            submission.setRevisionNumber(0);
            submission.setRevision(false);
        } else {
            submission = existing;
            submission.setMarkdownContent(markdownContent);
            submission.setUpdatedAt(now);
            submission.setRevisionNumber(existing.getRevisionNumber() + 1);
            submission.setRevision(true);
        }

        DeliverableSubmission saved = submissionRepository.save(submission);

        applicationEventPublisher.publishEvent(
                new DeliverableSubmissionCreatedEvent(
                        saved.getId(),
                        group.getId(),
                        deliverable.getId()
                )
        );

        DeliverableSubmissionResponse response = new DeliverableSubmissionResponse();
        response.setSubmissionId(saved.getId());
        response.setGroupId(group.getId());
        response.setDeliverableId(deliverable.getId());
        response.setSubmittedAt(saved.getSubmittedAt());
        response.setRevisionNumber(saved.getRevisionNumber());
        response.setRevision(saved.isRevision());
        return response;
    }

    @Transactional
    public DeliverableSubmissionResponse updateSubmission(
            UUID submissionId,
            UUID requesterUUID,
            String markdownContent) {

        DeliverableSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found"));

        GroupMembership membership = groupMembershipRepository.findByStudentId(requesterUUID)
                .orElseThrow(() -> new ForbiddenException(
                        "Only the Team Leader can update deliverables"));

        if (membership.getRole() != MemberRole.TEAM_LEADER) {
            throw new ForbiddenException("Only the Team Leader can update deliverables");
        }

        ProjectGroup group = membership.getGroup();
        if (!group.getId().equals(submission.getGroup().getId())) {
            throw new ForbiddenException("Only the Team Leader can update deliverables");
        }

        if (group.getStatus() == GroupStatus.DISBANDED) {
            throw new BusinessRuleException("Cannot update deliverable for a disbanded group");
        }

        Deliverable deliverable = submission.getDeliverable();
        LocalDateTime now = nowUtc();
        if (now.isAfter(deliverable.getReviewDeadline())) {
            throw new BusinessRuleException("Final grading deadline has passed");
        }

        submission.setMarkdownContent(markdownContent);
        submission.setUpdatedAt(now);
        DeliverableSubmission saved = submissionRepository.save(submission);

        DeliverableSubmissionResponse response = new DeliverableSubmissionResponse();
        response.setSubmissionId(saved.getId());
        response.setGroupId(group.getId());
        response.setDeliverableId(deliverable.getId());
        response.setSubmittedAt(saved.getSubmittedAt());
        response.setRevisionNumber(saved.getRevisionNumber());
        response.setRevision(saved.isRevision());
        return response;
    }

    @Transactional(readOnly = true)
    public DeliverableSubmissionDetailResponse getSubmission(UUID submissionId, UUID requesterUUID, String role) {
        DeliverableSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found"));

        ProjectGroup group = submission.getGroup();
        Deliverable deliverable = submission.getDeliverable();

        String normalizedRole = role == null ? "" : role.toUpperCase(Locale.ROOT);
        if ("STUDENT".equals(normalizedRole)) {
            GroupMembership membership = groupMembershipRepository.findByStudentId(requesterUUID)
                    .orElseThrow(() -> new ForbiddenException(
                            "You can only view submissions from your own group"));
            if (!membership.getGroup().getId().equals(group.getId())) {
                throw new ForbiddenException("You can only view submissions from your own group");
            }
        } else if ("PROFESSOR".equals(normalizedRole)) {
            boolean assigned = committeeRepository.existsByProfessorIdAndGroupIdAndDeliverableId(
                    requesterUUID, group.getId(), deliverable.getId());
            if (!assigned) {
                throw new ForbiddenException(
                        "You can only view submissions assigned to your committee");
            }
        } else {
            throw new ForbiddenException("Not permitted to view this submission");
        }

        DeliverableSubmissionDetailResponse response = new DeliverableSubmissionDetailResponse();
        response.setSubmissionId(submission.getId());
        response.setGroupId(group.getId());
        response.setDeliverableId(deliverable.getId());
        response.setMarkdownContent(submission.getMarkdownContent());
        response.setSubmittedAt(submission.getSubmittedAt());
        response.setUpdatedAt(submission.getUpdatedAt());
        response.setRevisionNumber(submission.getRevisionNumber());
        response.setRevision(submission.isRevision());
        return response;
    }

    @Transactional(readOnly = true)
    public List<DeliverableWithStatusResponse> listDeliverablesForStudent(UUID requesterUUID) {
        GroupMembership membership = groupMembershipRepository.findByStudentId(requesterUUID)
                .orElse(null);

        java.util.Map<UUID, UUID> submissionIdByDeliverable = membership == null
                ? java.util.Collections.emptyMap()
                : submissionRepository.findByGroup(membership.getGroup()).stream()
                        .sorted(java.util.Comparator.comparing(
                                DeliverableSubmission::getSubmittedAt,
                                java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder()))
                                .reversed())
                        .collect(java.util.stream.Collectors.toMap(
                                s -> s.getDeliverable().getId(),
                                DeliverableSubmission::getId,
                                (latest, older) -> latest));

        List<Deliverable> deliverables = deliverableRepository.findAll();
        return deliverables.stream().map(d -> {
            DeliverableWithStatusResponse dto = new DeliverableWithStatusResponse();
            dto.setId(d.getId());
            dto.setName(d.getName());
            dto.setType(d.getType());
            dto.setSubmissionDeadline(d.getSubmissionDeadline());
            dto.setReviewDeadline(d.getReviewDeadline());
            dto.setWeight(d.getWeight());
            UUID submissionId = submissionIdByDeliverable.get(d.getId());
            dto.setSubmissionStatus(submissionId != null
                    ? SubmissionStatus.SUBMITTED
                    : SubmissionStatus.NOT_SUBMITTED);
            dto.setSubmissionId(submissionId);
            return dto;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<CommitteeSubmissionSummaryResponse> listCommitteeSubmissions(UUID committeeId, UUID requesterUUID) {
        Committee committee = committeeRepository.findById(committeeId)
                .orElseThrow(() -> new NotFoundException("Committee not found"));

        boolean isMember = committee.getProfessors().stream()
                .anyMatch(committeeProfessor -> committeeProfessor.getProfessor().getId().equals(requesterUUID));
        if (!isMember) {
            throw new ForbiddenException("You are not a member of this committee");
        }

        Deliverable deliverable = committee.getDeliverable();
        return committee.getGroups().stream()
                .map(group -> submissionRepository
                        .findFirstByGroupAndDeliverableOrderBySubmittedAtDesc(group, deliverable))
                .filter(java.util.Objects::nonNull)
                .map(s -> {
                    CommitteeSubmissionSummaryResponse dto = new CommitteeSubmissionSummaryResponse();
                    dto.setSubmissionId(s.getId());
                    dto.setGroupId(s.getGroup().getId());
                    dto.setGroupName(s.getGroup().getGroupName());
                    dto.setDeliverableId(s.getDeliverable().getId());
                    dto.setDeliverableName(s.getDeliverable().getName());
                    dto.setSubmittedAt(s.getSubmittedAt());
                    dto.setUpdatedAt(s.getUpdatedAt());
                    dto.setRevisionNumber(s.getRevisionNumber());
                    dto.setRevision(s.isRevision());
                    dto.setCommentCount(submissionCommentRepository.countBySubmission(s));
                    return dto;
                })
                .toList();
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneId.of("UTC"));
    }
}