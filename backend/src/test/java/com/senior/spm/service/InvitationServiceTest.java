package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
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
import com.senior.spm.exception.GroupNotFoundException;
import com.senior.spm.exception.InvitationNotFoundException;
import com.senior.spm.exception.InvitationNotPendingException;
import com.senior.spm.repository.GroupInvitationRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.StudentRepository;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

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
        group.setGroupName("Team Alpha");
        group.setStatus(GroupStatus.FORMING);

        Student leader = new Student();
        leader.setId(requesterId);
        leader.setStudentId("23070000001");

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
        invitation.setSentAt(LocalDateTime.now());
    }

    @Test
    void sendInvitation_success_returnsCreatedResponse() {
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMembershipRepository.findByGroupIdAndStudentId(groupId, requesterId))
            .thenReturn(Optional.of(leaderMembership));
        when(groupMembershipRepository.countByGroupId(groupId)).thenReturn(1L);
        when(groupInvitationRepository.countByGroupIdAndStatus(groupId, InvitationStatus.PENDING)).thenReturn(0L);
        when(termConfigService.getMaxTeamSize()).thenReturn(5);
        when(studentRepository.findByStudentId("23070000002")).thenReturn(Optional.of(invitee));
        when(groupMembershipRepository.existsByStudentId(inviteeId)).thenReturn(false);
        when(groupInvitationRepository.existsByGroupIdAndInviteeIdAndStatus(groupId, inviteeId, InvitationStatus.PENDING))
            .thenReturn(false);
        when(groupInvitationRepository.save(any(GroupInvitation.class))).thenAnswer(invocation -> {
            GroupInvitation saved = invocation.getArgument(0);
            saved.setId(invitation.getId());
            return saved;
        });

        InvitationResponse response = invitationService.sendInvitation(groupId, requesterId, "23070000002");

        assertThat(response.getInvitationId()).isEqualTo(invitation.getId());
        assertThat(response.getGroupId()).isEqualTo(groupId);
        assertThat(response.getTargetStudentId()).isEqualTo("23070000002");
        assertThat(response.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void sendInvitation_nonLeader_throwsForbiddenException() {
        GroupMembership member = new GroupMembership();
        member.setRole(MemberRole.MEMBER);

        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMembershipRepository.findByGroupIdAndStudentId(groupId, requesterId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> invitationService.sendInvitation(groupId, requesterId, "23070000002"))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("Team Leader");
    }

    @Test
    void sendInvitation_groupNotFound_throwsGroupNotFoundException() {
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invitationService.sendInvitation(groupId, requesterId, "23070000002"))
            .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    void sendInvitation_disbandedGroup_throwsBusinessRuleException() {
        group.setStatus(GroupStatus.DISBANDED);
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMembershipRepository.findByGroupIdAndStudentId(groupId, requesterId))
            .thenReturn(Optional.of(leaderMembership));

        assertThatThrownBy(() -> invitationService.sendInvitation(groupId, requesterId, "23070000002"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("disbanded");
    }

    @Test
    void sendInvitation_capacityReached_throwsBusinessRuleException() {
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMembershipRepository.findByGroupIdAndStudentId(groupId, requesterId))
            .thenReturn(Optional.of(leaderMembership));
        when(groupMembershipRepository.countByGroupId(groupId)).thenReturn(2L);
        when(groupInvitationRepository.countByGroupIdAndStatus(groupId, InvitationStatus.PENDING)).thenReturn(3L);
        when(termConfigService.getMaxTeamSize()).thenReturn(5);

        assertThatThrownBy(() -> invitationService.sendInvitation(groupId, requesterId, "23070000002"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("maximum team size");
    }

    @Test
    void sendInvitation_missingStudent_throwsBusinessRuleException() {
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMembershipRepository.findByGroupIdAndStudentId(groupId, requesterId))
            .thenReturn(Optional.of(leaderMembership));
        when(groupMembershipRepository.countByGroupId(groupId)).thenReturn(1L);
        when(groupInvitationRepository.countByGroupIdAndStatus(groupId, InvitationStatus.PENDING)).thenReturn(0L);
        when(termConfigService.getMaxTeamSize()).thenReturn(5);
        when(studentRepository.findByStudentId("23070009999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invitationService.sendInvitation(groupId, requesterId, "23070009999"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("not registered");
    }

    @Test
    void sendInvitation_targetAlreadyGrouped_throwsAlreadyInGroupException() {
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMembershipRepository.findByGroupIdAndStudentId(groupId, requesterId))
            .thenReturn(Optional.of(leaderMembership));
        when(groupMembershipRepository.countByGroupId(groupId)).thenReturn(1L);
        when(groupInvitationRepository.countByGroupIdAndStatus(groupId, InvitationStatus.PENDING)).thenReturn(0L);
        when(termConfigService.getMaxTeamSize()).thenReturn(5);
        when(studentRepository.findByStudentId("23070000002")).thenReturn(Optional.of(invitee));
        when(groupMembershipRepository.existsByStudentId(inviteeId)).thenReturn(true);

        assertThatThrownBy(() -> invitationService.sendInvitation(groupId, requesterId, "23070000002"))
            .isInstanceOf(AlreadyInGroupException.class);
    }

    @Test
    void sendInvitation_duplicatePending_throwsDuplicateInvitationException() {
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMembershipRepository.findByGroupIdAndStudentId(groupId, requesterId))
            .thenReturn(Optional.of(leaderMembership));
        when(groupMembershipRepository.countByGroupId(groupId)).thenReturn(1L);
        when(groupInvitationRepository.countByGroupIdAndStatus(groupId, InvitationStatus.PENDING)).thenReturn(0L);
        when(termConfigService.getMaxTeamSize()).thenReturn(5);
        when(studentRepository.findByStudentId("23070000002")).thenReturn(Optional.of(invitee));
        when(groupMembershipRepository.existsByStudentId(inviteeId)).thenReturn(false);
        when(groupInvitationRepository.existsByGroupIdAndInviteeIdAndStatus(groupId, inviteeId, InvitationStatus.PENDING))
            .thenReturn(true);

        assertThatThrownBy(() -> invitationService.sendInvitation(groupId, requesterId, "23070000002"))
            .isInstanceOf(DuplicateInvitationException.class);
    }

    @Test
    void getPendingInvitations_returnsInboxShape() {
        when(groupInvitationRepository.findByInviteeIdAndStatus(inviteeId, InvitationStatus.PENDING))
            .thenReturn(List.of(invitation));
        when(groupMembershipRepository.findByGroupIdAndRole(groupId, MemberRole.TEAM_LEADER))
            .thenReturn(Optional.of(leaderMembership));

        List<InvitationResponse> responses = invitationService.getPendingInvitations(inviteeId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getGroupId()).isEqualTo(groupId);
        assertThat(responses.get(0).getGroupName()).isEqualTo("Team Alpha");
        assertThat(responses.get(0).getTeamLeaderStudentId()).isEqualTo("23070000001");
        assertThat(responses.get(0).getTargetStudentId()).isNull();
    }

    @Test
    void cancelInvitation_nonPending_throwsInvitationNotPendingException() {
        invitation.setStatus(InvitationStatus.DECLINED);
        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(groupMembershipRepository.findByGroupIdAndStudentId(groupId, requesterId))
            .thenReturn(Optional.of(leaderMembership));

        assertThatThrownBy(() -> invitationService.cancelInvitation(invitation.getId(), requesterId))
            .isInstanceOf(InvitationNotPendingException.class);
    }

    @Test
    void cancelInvitation_notFound_throwsInvitationNotFoundException() {
        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invitationService.cancelInvitation(invitation.getId(), requesterId))
            .isInstanceOf(InvitationNotFoundException.class);
    }

    @Test
    void respondToInvitation_decline_returnsDeclinedResponse() {
        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(groupInvitationRepository.save(any(GroupInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InvitationResponse response = (InvitationResponse) invitationService.respondToInvitation(
            invitation.getId(),
            inviteeId,
            false
        );

        assertThat(response.getInvitationId()).isEqualTo(invitation.getId());
        assertThat(response.getStatus()).isEqualTo("DECLINED");
    }

    @Test
    void respondToInvitation_wrongInvitee_throwsForbiddenException() {
        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> invitationService.respondToInvitation(invitation.getId(), UUID.randomUUID(), false))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("does not belong");
    }

    @Test
    void respondToInvitation_accept_success_createsMembershipAndAutoDenies() {
        GroupDetailResponse groupDetailResponse = new GroupDetailResponse();
        groupDetailResponse.setId(groupId);

        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMembershipRepository.countByGroupId(groupId)).thenReturn(1L);
        when(termConfigService.getMaxTeamSize()).thenReturn(5);
        when(groupMembershipRepository.existsByStudentId(inviteeId)).thenReturn(false);
        when(groupInvitationRepository.saveAndFlush(any(GroupInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(groupService.getGroupDetail(groupId)).thenReturn(groupDetailResponse);

        GroupDetailResponse response = (GroupDetailResponse) invitationService.respondToInvitation(
            invitation.getId(),
            inviteeId,
            true
        );

        assertThat(response.getId()).isEqualTo(groupId);

        ArgumentCaptor<GroupMembership> captor = ArgumentCaptor.forClass(GroupMembership.class);
        verify(groupMembershipRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(captor.getValue().getStudent()).isEqualTo(invitee);
        verify(groupInvitationRepository).autoDenyOtherPendingInvitationsExcept(inviteeId, invitation.getId());
    }

    @Test
    void respondToInvitation_accept_persistsAcceptedStateBeforeAutoDenyingOthers() {
        GroupDetailResponse groupDetailResponse = new GroupDetailResponse();
        groupDetailResponse.setId(groupId);

        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMembershipRepository.countByGroupId(groupId)).thenReturn(1L);
        when(termConfigService.getMaxTeamSize()).thenReturn(5);
        when(groupMembershipRepository.existsByStudentId(inviteeId)).thenReturn(false);
        when(groupInvitationRepository.saveAndFlush(any(GroupInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(groupService.getGroupDetail(groupId)).thenReturn(groupDetailResponse);

        GroupDetailResponse response = (GroupDetailResponse) invitationService.respondToInvitation(
            invitation.getId(),
            inviteeId,
            true
        );

        assertThat(response.getId()).isEqualTo(groupId);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(invitation.getRespondedAt()).isNotNull();

        InOrder inOrder = inOrder(groupInvitationRepository, groupMembershipRepository, groupService);
        inOrder.verify(groupInvitationRepository).saveAndFlush(invitation);
        inOrder.verify(groupMembershipRepository).save(any(GroupMembership.class));
        inOrder.verify(groupInvitationRepository).autoDenyOtherPendingInvitationsExcept(inviteeId, invitation.getId());
        inOrder.verify(groupService).getGroupDetail(groupId);
    }

    @Test
    void respondToInvitation_accept_whenCurrentMembersAtMax_throwsBusinessRuleException() {
        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMembershipRepository.countByGroupId(groupId)).thenReturn(5L);
        when(termConfigService.getMaxTeamSize()).thenReturn(5);

        assertThatThrownBy(() -> invitationService.respondToInvitation(invitation.getId(), inviteeId, true))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("maximum team size");

        verify(groupInvitationRepository, never()).saveAndFlush(any(GroupInvitation.class));
    }

    @Test
    void respondToInvitation_accept_whenMembersPlusPendingHitsMaxStillSucceeds() {
        GroupDetailResponse groupDetailResponse = new GroupDetailResponse();
        groupDetailResponse.setId(groupId);

        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMembershipRepository.countByGroupId(groupId)).thenReturn(1L);
        when(termConfigService.getMaxTeamSize()).thenReturn(5);
        when(groupMembershipRepository.existsByStudentId(inviteeId)).thenReturn(false);
        when(groupInvitationRepository.saveAndFlush(any(GroupInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(groupService.getGroupDetail(groupId)).thenReturn(groupDetailResponse);

        GroupDetailResponse response = (GroupDetailResponse) invitationService.respondToInvitation(
            invitation.getId(),
            inviteeId,
            true
        );

        assertThat(response.getId()).isEqualTo(groupId);
        verify(groupInvitationRepository).saveAndFlush(any(GroupInvitation.class));
    }

    @Test
    void respondToInvitation_accept_lockedGroup_throwsBusinessRuleException() {
        group.setStatus(GroupStatus.TOOLS_BOUND);
        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> invitationService.respondToInvitation(invitation.getId(), inviteeId, true))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("locked");
    }

    @Test
    void respondToInvitation_accept_disbandedGroup_throwsBusinessRuleException() {
        group.setStatus(GroupStatus.DISBANDED);
        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> invitationService.respondToInvitation(invitation.getId(), inviteeId, true))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("disbanded");
    }

    @Test
    void respondToInvitation_accept_whenAlreadyGrouped_throwsBusinessRuleException() {
        when(groupInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMembershipRepository.countByGroupId(groupId)).thenReturn(1L);
        when(termConfigService.getMaxTeamSize()).thenReturn(5);
        when(groupMembershipRepository.existsByStudentId(inviteeId)).thenReturn(true);

        assertThatThrownBy(() -> invitationService.respondToInvitation(invitation.getId(), inviteeId, true))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("already a member");
    }
}
