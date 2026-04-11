package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.senior.spm.controller.dto.GroupDetailResponse;
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
import com.senior.spm.exception.InvitationNotFoundException;
import com.senior.spm.exception.InvitationNotPendingException;
import com.senior.spm.repository.GroupInvitationRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.StudentRepository;

@ExtendWith(MockitoExtension.class)
class InvitationServiceEdgeCaseTest {

    @Mock private GroupInvitationRepository groupInvitationRepository;
    @Mock private GroupMembershipRepository groupMembershipRepository;
    @Mock private ProjectGroupRepository projectGroupRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private TermConfigService termConfigService;
    @Mock private GroupService groupService;

    @InjectMocks
    private InvitationService invitationService;

    private UUID groupId;
    private UUID requesterId;
    private UUID inviteeId;
    private ProjectGroup group;
    private Student invitee;
    private GroupMembership leaderMembership;
    private GroupInvitation invitation;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
        inviteeId = UUID.randomUUID();

        group = new ProjectGroup();
        group.setId(groupId);
        group.setGroupName("Team Edge");
        group.setStatus(GroupStatus.FORMING);

        Student leader = new Student();
        leader.setId(requesterId);

        invitee = new Student();
        invitee.setId(inviteeId);
        invitee.setStudentId("23070000002");

        leaderMembership = new GroupMembership();
        leaderMembership.setGroup(group);
        leaderMembership.setStudent(leader);
        leaderMembership.setRole(MemberRole.TEAM_LEADER);

        invitation = new GroupInvitation();
        invitation.setId(UUID.randomUUID());
        invitation.setGroup(group);
        invitation.setInvitee(invitee);
        invitation.setStatus(InvitationStatus.PENDING);
    }

    // --- sendInvitation Edge Cases ---

    @Test
    @DisplayName("Send: Should fail if group is at capacity (members + pending)")
    void sendInvitation_atCapacity_throwsException() {
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMembershipRepository.findByGroupIdAndStudentId(groupId, requesterId)).thenReturn(Optional.of(leaderMembership));
        when(groupMembershipRepository.countByGroupId(groupId)).thenReturn(3L);
        when(groupInvitationRepository.countByGroupIdAndStatus(groupId, InvitationStatus.PENDING)).thenReturn(2L);
        when(termConfigService.getMaxTeamSize()).thenReturn(5);

        assertThatThrownBy(() -> invitationService.sendInvitation(groupId, requesterId, "23070000002"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("maximum team size");
    }

    @Test
    @DisplayName("Send: Should fail if group is DISBANDED")
    void sendInvitation_disbanded_throwsException() {
        group.setStatus(GroupStatus.DISBANDED);
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMembershipRepository.findByGroupIdAndStudentId(groupId, requesterId)).thenReturn(Optional.of(leaderMembership));

        assertThatThrownBy(() -> invitationService.sendInvitation(groupId, requesterId, "23070000002"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("disbanded");
    }

    @Test
    @DisplayName("Send: Should fail if duplicate pending invitation already exists")
    void sendInvitation_duplicatePending_throwsException() {
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMembershipRepository.findByGroupIdAndStudentId(groupId, requesterId)).thenReturn(Optional.of(leaderMembership));
        when(groupMembershipRepository.countByGroupId(groupId)).thenReturn(1L);
        when(groupInvitationRepository.countByGroupIdAndStatus(groupId, InvitationStatus.PENDING)).thenReturn(0L);
        when(termConfigService.getMaxTeamSize()).thenReturn(5);
        when(studentRepository.findByStudentId(invitee.getStudentId())).thenReturn(Optional.of(invitee));
        when(groupMembershipRepository.existsByStudentId(inviteeId)).thenReturn(false);
        when(groupInvitationRepository.existsByGroupIdAndInviteeIdAndStatus(groupId, inviteeId, InvitationStatus.PENDING))
            .thenReturn(true);

        assertThatThrownBy(() -> invitationService.sendInvitation(groupId, requesterId, invitee.getStudentId()))
            .isInstanceOf(DuplicateInvitationException.class);
    }

    @Test
    @DisplayName("Send: Should fail if invitee is already a member of any group")
    void sendInvitation_inviteeAlreadyInGroup_throwsAlreadyInGroupException() {
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMembershipRepository.findByGroupIdAndStudentId(groupId, requesterId)).thenReturn(Optional.of(leaderMembership));
        when(groupMembershipRepository.countByGroupId(groupId)).thenReturn(1L);
        when(groupInvitationRepository.countByGroupIdAndStatus(groupId, InvitationStatus.PENDING)).thenReturn(0L);
        when(termConfigService.getMaxTeamSize()).thenReturn(5);
        when(studentRepository.findByStudentId(invitee.getStudentId())).thenReturn(Optional.of(invitee));
        when(groupMembershipRepository.existsByStudentId(inviteeId)).thenReturn(true);

        assertThatThrownBy(() -> invitationService.sendInvitation(groupId, requesterId, invitee.getStudentId()))
            .isInstanceOf(AlreadyInGroupException.class);
    }

    // --- respondToInvitation Edge Cases ---

    @Test
    @DisplayName("Accept: Should succeed if (members < max) even if (members + pending >= max)")
    void respondToInvitation_accept_ignoresPendingOutboundCapacity() {
        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        
        when(groupMembershipRepository.countByGroupId(groupId)).thenReturn(4L);
        when(termConfigService.getMaxTeamSize()).thenReturn(5);
        when(groupMembershipRepository.existsByStudentId(inviteeId)).thenReturn(false);
        when(groupService.getGroupDetail(groupId)).thenReturn(new GroupDetailResponse());

        Object response = invitationService.respondToInvitation(invitation.getId(), inviteeId, true);

        assertThat(response).isInstanceOf(GroupDetailResponse.class);
        verify(groupMembershipRepository).save(any(GroupMembership.class));
        verify(groupInvitationRepository).autoDenyOtherPendingInvitationsExcept(eq(inviteeId), eq(invitation.getId()));
    }

    @Test
    @DisplayName("Accept: Fail if student accepts someone else's invitation")
    void respondToInvitation_wrongStudent_throwsForbidden() {
        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        UUID randomStudentId = UUID.randomUUID();

        assertThatThrownBy(() -> invitationService.respondToInvitation(invitation.getId(), randomStudentId, true))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("does not belong to you");
    }

    @Test
    @DisplayName("Decline: Should correctly decline without affecting other invites")
    void respondToInvitation_decline_isIsolated() {
        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(groupInvitationRepository.save(any(GroupInvitation.class))).thenAnswer(i -> i.getArgument(0));

        Object response = invitationService.respondToInvitation(invitation.getId(), inviteeId, false);

        assertThat(response).isInstanceOf(InvitationResponse.class);
        assertThat(((InvitationResponse)response).getStatus()).isEqualTo("DECLINED");
        
        // Ensure auto-deny logic was NOT triggered
        verify(groupInvitationRepository, never()).autoDenyOtherPendingInvitationsExcept(any(), any());
    }

    @Test
    @DisplayName("Accept: Roster lock - fail if status is TOOLS_BOUND")
    void respondToInvitation_accept_rosterLocked_toolsBound() {
        group.setStatus(GroupStatus.TOOLS_BOUND);
        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> invitationService.respondToInvitation(invitation.getId(), inviteeId, true))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("locked");
    }

    @Test
    @DisplayName("Accept: Roster lock - fail if status is ADVISOR_ASSIGNED")
    void respondToInvitation_accept_rosterLocked_advisorAssigned() {
        group.setStatus(GroupStatus.ADVISOR_ASSIGNED);
        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> invitationService.respondToInvitation(invitation.getId(), inviteeId, true))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("locked");
    }

    @Test
    @DisplayName("Accept: Fail if group was DISBANDED while invite was pending")
    void respondToInvitation_accept_disbandedGroup_throwsException() {
        group.setStatus(GroupStatus.DISBANDED);
        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> invitationService.respondToInvitation(invitation.getId(), inviteeId, true))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("disbanded");
    }

    // --- RBAC & Authorization Edge Cases ---

    @Test
    @DisplayName("List: Only TEAM_LEADER can view group invitations")
    void getGroupInvitations_nonLeader_throwsForbidden() {
        GroupMembership member = new GroupMembership();
        member.setRole(MemberRole.MEMBER);
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMembershipRepository.findByGroupIdAndStudentId(groupId, requesterId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> invitationService.getGroupInvitations(groupId, requesterId))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("Only the Team Leader");
    }

    @Test
    @DisplayName("Cancel: Only TEAM_LEADER can cancel invitations")
    void cancelInvitation_nonLeader_throwsForbidden() {
        GroupMembership member = new GroupMembership();
        member.setRole(MemberRole.MEMBER);
        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(groupMembershipRepository.findByGroupIdAndStudentId(groupId, requesterId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> invitationService.cancelInvitation(invitation.getId(), requesterId))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("Only the Team Leader");
    }

    @Test
    @DisplayName("Cancel: Should only be possible for PENDING invitations")
    void cancelInvitation_notPending_throwsException() {
        invitation.setStatus(InvitationStatus.ACCEPTED);
        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(groupMembershipRepository.findByGroupIdAndStudentId(groupId, requesterId))
            .thenReturn(Optional.of(leaderMembership));

        assertThatThrownBy(() -> invitationService.cancelInvitation(invitation.getId(), requesterId))
            .isInstanceOf(InvitationNotPendingException.class);
    }

    @Test
    @DisplayName("Cancel: Should set respondedAt")
    void cancelInvitation_setsRespondedAt() {
        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(groupMembershipRepository.findByGroupIdAndStudentId(groupId, requesterId))
            .thenReturn(Optional.of(leaderMembership));
        when(groupInvitationRepository.save(any(GroupInvitation.class))).thenAnswer(i -> i.getArgument(0));

        InvitationResponse response = invitationService.cancelInvitation(invitation.getId(), requesterId);

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        assertThat(invitation.getRespondedAt()).isNotNull();
    }
}
