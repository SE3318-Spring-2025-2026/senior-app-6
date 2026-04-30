package com.senior.spm.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.response.DeliverableSubmissionResponse;
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

        DeliverableSubmission previousLatest =
                submissionRepository.findFirstByGroupAndDeliverableOrderBySubmittedAtDesc(group, deliverable);

        DeliverableSubmission submission = new DeliverableSubmission();
        submission.setGroup(group);
        submission.setDeliverable(deliverable);
        submission.setMarkdownContent(markdownContent);
        submission.setSubmittedAt(now);
        if (previousLatest == null) {
            submission.setRevisionNumber(0);
            submission.setRevision(false);
        } else {
            submission.setRevisionNumber(previousLatest.getRevisionNumber() + 1);
            submission.setRevision(true);
        }

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

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneId.of("UTC"));
    }
}
