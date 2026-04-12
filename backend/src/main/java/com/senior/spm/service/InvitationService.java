package com.senior.spm.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.controller.dto.InvitationActionResponse;
import com.senior.spm.controller.dto.InvitationResponse;
import com.senior.spm.entity.GroupInvitation;
import com.senior.spm.entity.GroupInvitation.InvitationStatus;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.GroupMembership.MemberRole;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.Student;
import com.senior.spm.exception.AlreadyInGroupException;
import com.senior.spm.exception.BusinessRuleException;
import com.senior.spm.exception.DuplicateInvitationException;
import com.senior.spm.exception.ForbiddenException;
import com.senior.spm.exception.GroupNotFoundException;
import com.senior.spm.exception.InvitationNotFoundException;
import com.senior.spm.exception.InvitationNotPendingException;
import com.senior.spm.repository.GroupInvitationRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.StudentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Coordinates the group invitation lifecycle for Process 2.
 *
 * <p>This service owns invitation-specific business rules such as team leader
 * authorization, invitation status transitions, max team size checks, roster
 * lock enforcement on accept, and the transactional accept flow that creates
 * group membership while auto-denying competing pending invitations.
 */
@Service
@RequiredArgsConstructor
public class InvitationService {

    private final GroupInvitationRepository groupInvitationRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final ProjectGroupRepository projectGroupRepository;
    private final StudentRepository studentRepository;
    private final TermConfigService termConfigService;
    private final GroupService groupService;

    /**
     * Create a new pending invitation from a team leader's group to a target student.
     *
     * @param groupId UUID of the inviting group
     * @param requesterUUID internal UUID of the authenticated requester
     * @param targetStudentId public student id of the invitee
     * @return created invitation summary
     * @throws GroupNotFoundException if the group does not exist
     * @throws ForbiddenException if the requester is not the team's leader
     * @throws BusinessRuleException if the group is disbanded, at capacity, or the student is unknown
     * @throws AlreadyInGroupException if the target student is already in a group
     * @throws DuplicateInvitationException if a pending invitation already exists for the same student
     */
    @Transactional
    public InvitationResponse sendInvitation(UUID groupId, UUID requesterUUID, String targetStudentId) {
        ProjectGroup group = projectGroupRepository.findById(groupId)
            .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        requireTeamLeader(groupId, requesterUUID, "Only the Team Leader can send invitations");

        if (group.getStatus() == GroupStatus.DISBANDED) {
            throw new BusinessRuleException("Cannot send invitation from a disbanded group");
        }

        // The send-side capacity rule counts current members plus outbound pending invitations.
        long currentMembers = groupMembershipRepository.countByGroupId(groupId);
        long pendingInvitations = groupInvitationRepository.countByGroupIdAndStatus(groupId, InvitationStatus.PENDING);
        if (currentMembers + pendingInvitations >= termConfigService.getMaxTeamSize()) {
            throw new BusinessRuleException("Group has reached maximum team size");
        }

        Student targetStudent = studentRepository.findByStudentId(targetStudentId)
            .orElseThrow(() -> new BusinessRuleException(
                String.format("Student '%s' is not registered in the system", targetStudentId)
            ));

        if (groupMembershipRepository.existsByStudentId(targetStudent.getId())) {
            throw new AlreadyInGroupException(
                String.format("Student '%s' is already a member of a group", targetStudentId)
            );
        }

        if (groupInvitationRepository.existsByGroupIdAndInviteeIdAndStatus(
            groupId, targetStudent.getId(), InvitationStatus.PENDING
        )) {
            throw new DuplicateInvitationException("A pending invitation already exists for this student");
        }

        GroupInvitation invitation = new GroupInvitation();
        invitation.setGroup(group);
        invitation.setInvitee(targetStudent);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setSentAt(nowUtc());

        return toSendInvitationResponse(groupInvitationRepository.save(invitation));
    }

    /**
     * List all invitations owned by a group after verifying the caller is the team leader.
     *
     * @param groupId UUID of the group whose invitations should be returned
     * @param requesterUUID internal UUID of the authenticated requester
     * @return all invitations for the group across all statuses
     * @throws GroupNotFoundException if the group does not exist
     * @throws ForbiddenException if the requester is not the team's leader
     */
    @Transactional(readOnly = true)
    public List<InvitationResponse> getGroupInvitations(UUID groupId, UUID requesterUUID) {
        projectGroupRepository.findById(groupId)
            .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        requireTeamLeader(groupId, requesterUUID, "Only the Team Leader can view group invitations");

        return groupInvitationRepository.findByGroupId(groupId).stream()
            .map(this::toGroupInvitationResponse)
            .toList();
    }

    /**
     * Return the pending invitation inbox for a student.
     *
     * @param studentUUID internal UUID of the authenticated student
     * @return pending invitations for the student, or an empty list when none exist
     */
    @Transactional(readOnly = true)
    public List<InvitationResponse> getPendingInvitations(UUID studentUUID) {
        return groupInvitationRepository.findByInviteeIdAndStatus(studentUUID, InvitationStatus.PENDING).stream()
            .map(this::toPendingInvitationResponse)
            .toList();
    }

    /**
     * Cancel a pending invitation on behalf of the inviting team's leader.
     *
     * @param invitationId UUID of the invitation to cancel
     * @param requesterUUID internal UUID of the authenticated requester
     * @return updated invitation summary
     * @throws InvitationNotFoundException if the invitation does not exist
     * @throws ForbiddenException if the requester is not the inviting team's leader
     * @throws InvitationNotPendingException if the invitation is already terminal
     */
    @Transactional
    public InvitationResponse cancelInvitation(UUID invitationId, UUID requesterUUID) {
        GroupInvitation invitation = groupInvitationRepository.findById(invitationId)
            .orElseThrow(() -> new InvitationNotFoundException("Invitation not found"));

        requireTeamLeader(
            invitation.getGroup().getId(),
            requesterUUID,
            "Only the Team Leader can cancel invitations"
        );

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new InvitationNotPendingException("Invitation is no longer pending");
        }

        invitation.setStatus(InvitationStatus.CANCELLED);
        invitation.setRespondedAt(nowUtc());
        return toStatusOnlyResponse(groupInvitationRepository.save(invitation));
    }

    /**
     * Accept or decline an invitation owned by the authenticated student.
     *
     * @param invitationId UUID of the invitation being answered
     * @param studentUUID internal UUID of the authenticated student
     * @param accept {@code true} to accept; {@code false} to decline
     * @return group detail on accept, invitation summary on decline
     * @throws InvitationNotFoundException if the invitation does not exist
     * @throws ForbiddenException if the invitation belongs to another student
     * @throws InvitationNotPendingException if the invitation is already terminal
     * @throws GroupNotFoundException if the source group no longer exists
     * @throws BusinessRuleException if accept is blocked by group status, capacity, or membership rules
     */
    @Transactional
    public InvitationActionResponse respondToInvitation(UUID invitationId, UUID studentUUID, boolean accept) {
        GroupInvitation invitation = groupInvitationRepository.findById(invitationId)
            .orElseThrow(() -> new InvitationNotFoundException("Invitation not found"));

        if (!invitation.getInvitee().getId().equals(studentUUID)) {
            throw new ForbiddenException("This invitation does not belong to you");
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new InvitationNotPendingException("Invitation is no longer pending");
        }

        if (!accept) {
            invitation.setStatus(InvitationStatus.DECLINED);
            invitation.setRespondedAt(nowUtc());
            return toStatusOnlyResponse(groupInvitationRepository.save(invitation));
        }

        ProjectGroup group = projectGroupRepository.findById(invitation.getGroup().getId())
            .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        if (group.getStatus() == GroupStatus.DISBANDED) {
            throw new BusinessRuleException("This group has been disbanded");
        }

        if (group.getStatus().locksRoster()) {
            throw new BusinessRuleException("Group roster is locked after tool binding");
        }

        // Accept consumes one pending seat, so only current member count matters here.
        long currentMembers = groupMembershipRepository.countByGroupId(group.getId());
        if (currentMembers >= termConfigService.getMaxTeamSize()) {
            throw new BusinessRuleException("Group has reached maximum team size");
        }

        if (groupMembershipRepository.existsByStudentId(studentUUID)) {
            throw new BusinessRuleException("You are already a member of a group");
        }

        LocalDateTime now = nowUtc();
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setRespondedAt(now);
        groupInvitationRepository.saveAndFlush(invitation);

        GroupMembership membership = new GroupMembership();
        membership.setGroup(group);
        membership.setStudent(invitation.getInvitee());
        membership.setRole(MemberRole.MEMBER);
        membership.setJoinedAt(now);
        groupMembershipRepository.save(membership);

        // Bulk update excludes the accepted invitation id so the final state is deterministic.
        groupInvitationRepository.autoDenyOtherPendingInvitationsExcept(studentUUID, invitationId);

        return groupService.getGroupDetail(group.getId());
    }

    private void requireTeamLeader(UUID groupId, UUID requesterUUID, String message) {
        GroupMembership membership = groupMembershipRepository.findByGroupIdAndStudentId(groupId, requesterUUID)
            .orElseThrow(() -> new ForbiddenException(message));

        if (membership.getRole() != MemberRole.TEAM_LEADER) {
            throw new ForbiddenException(message);
        }
    }

    private InvitationResponse toSendInvitationResponse(GroupInvitation invitation) {
        InvitationResponse response = new InvitationResponse();
        response.setInvitationId(invitation.getId());
        response.setGroupId(invitation.getGroup().getId());
        response.setTargetStudentId(invitation.getInvitee().getStudentId());
        response.setStatus(invitation.getStatus().toString());
        response.setSentAt(invitation.getSentAt());
        return response;
    }

    private InvitationResponse toGroupInvitationResponse(GroupInvitation invitation) {
        InvitationResponse response = new InvitationResponse();
        response.setInvitationId(invitation.getId());
        response.setTargetStudentId(invitation.getInvitee().getStudentId());
        response.setStatus(invitation.getStatus().toString());
        response.setSentAt(invitation.getSentAt());
        response.setRespondedAt(invitation.getRespondedAt());
        return response;
    }

    private InvitationResponse toPendingInvitationResponse(GroupInvitation invitation) {
        InvitationResponse response = new InvitationResponse();
        response.setInvitationId(invitation.getId());
        response.setGroupId(invitation.getGroup().getId());
        response.setGroupName(invitation.getGroup().getGroupName());
        response.setStatus(invitation.getStatus().toString());
        // TODO: Batch-load team leaders if pending inbox sizes grow beyond the current small roster limit.
        response.setTeamLeaderStudentId(
            groupMembershipRepository.findByGroupIdAndRole(invitation.getGroup().getId(), MemberRole.TEAM_LEADER)
                .map(GroupMembership::getStudent)
                .map(Student::getStudentId)
                .orElse(null)
        );
        response.setSentAt(invitation.getSentAt());
        return response;
    }

    private InvitationResponse toStatusOnlyResponse(GroupInvitation invitation) {
        InvitationResponse response = new InvitationResponse();
        response.setInvitationId(invitation.getId());
        response.setStatus(invitation.getStatus().toString());
        response.setRespondedAt(invitation.getRespondedAt());
        return response;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneId.of("UTC"));
    }
}
