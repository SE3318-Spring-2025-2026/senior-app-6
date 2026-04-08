package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.senior.spm.controller.dto.GroupDetailResponse;
import com.senior.spm.entity.AdvisorRequest.RequestStatus;
import com.senior.spm.entity.GroupInvitation.InvitationStatus;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.GroupMembership.MemberRole;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.ScheduleWindow;
import com.senior.spm.entity.ScheduleWindow.WindowType;
import com.senior.spm.entity.Student;
import com.senior.spm.exception.AlreadyInGroupException;
import com.senior.spm.exception.BusinessRuleException;
import com.senior.spm.exception.DuplicateGroupNameException;
import com.senior.spm.exception.ForbiddenException;
import com.senior.spm.exception.GroupNotFoundException;
import com.senior.spm.exception.ScheduleWindowClosedException;
import com.senior.spm.exception.StudentNotFoundException;
import com.senior.spm.repository.AdvisorRequestRepository;
import com.senior.spm.repository.GroupInvitationRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.ScheduleWindowRepository;
import com.senior.spm.repository.StudentRepository;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock private ScheduleWindowRepository scheduleWindowRepository;
    @Mock private ProjectGroupRepository projectGroupRepository;
    @Mock private GroupMembershipRepository groupMembershipRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private GroupInvitationRepository groupInvitationRepository;
    @Mock private AdvisorRequestRepository advisorRequestRepository;
    @Mock private TermConfigService termConfigService;

    @InjectMocks
    private GroupService groupService;

    private static final String TERM_ID = "2024-FALL";
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final String GROUP_NAME = "TeamAlpha";

    private ScheduleWindow openWindow;
    private Student student;
    private ProjectGroup savedGroup;

    @BeforeEach
    void setUp() {
        openWindow = new ScheduleWindow();
        openWindow.setId(UUID.randomUUID());
        openWindow.setType(WindowType.GROUP_CREATION);
        openWindow.setTermId(TERM_ID);
        openWindow.setOpensAt(LocalDateTime.now().minusDays(1));
        openWindow.setClosesAt(LocalDateTime.now().plusDays(1));

        student = new Student();
        student.setId(STUDENT_ID);

        savedGroup = new ProjectGroup();
        savedGroup.setId(UUID.randomUUID());
        savedGroup.setGroupName(GROUP_NAME);
        savedGroup.setTermId(TERM_ID);
        savedGroup.setStatus(GroupStatus.FORMING);
        savedGroup.setCreatedAt(LocalDateTime.now());
    }

    // ── createGroup ────────────────────────────────────────────────────────────

    @Test
    void createGroup_success_returnsGroupDetailResponse() {
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
        when(scheduleWindowRepository.findByTermIdAndType(TERM_ID, WindowType.GROUP_CREATION))
                .thenReturn(Optional.of(openWindow));
        when(groupMembershipRepository.existsByStudentId(STUDENT_ID)).thenReturn(false);
        when(projectGroupRepository.existsByGroupNameAndTermId(GROUP_NAME, TERM_ID)).thenReturn(false);
        when(projectGroupRepository.save(any())).thenReturn(savedGroup);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(groupMembershipRepository.save(any())).thenReturn(new GroupMembership());
        when(groupMembershipRepository.findByGroupId(savedGroup.getId())).thenReturn(List.of());

        GroupDetailResponse response = groupService.createGroup(GROUP_NAME, STUDENT_ID);

        assertThat(response.getGroupName()).isEqualTo(GROUP_NAME);
        assertThat(response.getTermId()).isEqualTo(TERM_ID);
        assertThat(response.getStatus()).isEqualTo("FORMING");
    }

    @Test
    void createGroup_savesGroupWithFormingStatus() {
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
        when(scheduleWindowRepository.findByTermIdAndType(TERM_ID, WindowType.GROUP_CREATION))
                .thenReturn(Optional.of(openWindow));
        when(groupMembershipRepository.existsByStudentId(STUDENT_ID)).thenReturn(false);
        when(projectGroupRepository.existsByGroupNameAndTermId(GROUP_NAME, TERM_ID)).thenReturn(false);
        when(projectGroupRepository.save(any())).thenReturn(savedGroup);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(groupMembershipRepository.save(any())).thenReturn(new GroupMembership());
        when(groupMembershipRepository.findByGroupId(any())).thenReturn(List.of());

        groupService.createGroup(GROUP_NAME, STUDENT_ID);

        ArgumentCaptor<ProjectGroup> captor = ArgumentCaptor.forClass(ProjectGroup.class);
        verify(projectGroupRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(GroupStatus.FORMING);
        assertThat(captor.getValue().getGroupName()).isEqualTo(GROUP_NAME);
        assertThat(captor.getValue().getTermId()).isEqualTo(TERM_ID);
    }

    @Test
    void createGroup_savesTeamLeaderMembership() {
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
        when(scheduleWindowRepository.findByTermIdAndType(TERM_ID, WindowType.GROUP_CREATION))
                .thenReturn(Optional.of(openWindow));
        when(groupMembershipRepository.existsByStudentId(STUDENT_ID)).thenReturn(false);
        when(projectGroupRepository.existsByGroupNameAndTermId(GROUP_NAME, TERM_ID)).thenReturn(false);
        when(projectGroupRepository.save(any())).thenReturn(savedGroup);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(groupMembershipRepository.save(any())).thenReturn(new GroupMembership());
        when(groupMembershipRepository.findByGroupId(any())).thenReturn(List.of());

        groupService.createGroup(GROUP_NAME, STUDENT_ID);

        ArgumentCaptor<GroupMembership> captor = ArgumentCaptor.forClass(GroupMembership.class);
        verify(groupMembershipRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(MemberRole.TEAM_LEADER);
        assertThat(captor.getValue().getStudent()).isEqualTo(student);
    }

    @Test
    void createGroup_windowNotFound_throwsScheduleWindowClosedException() {
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
        when(scheduleWindowRepository.findByTermIdAndType(TERM_ID, WindowType.GROUP_CREATION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.createGroup(GROUP_NAME, STUDENT_ID))
                .isInstanceOf(ScheduleWindowClosedException.class)
                .hasMessageContaining("not currently active");

        verify(projectGroupRepository, never()).save(any());
    }

    @Test
    void createGroup_windowExpired_throwsScheduleWindowClosedException() {
        openWindow.setClosesAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(1)); // already closed in UTC

        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
        when(scheduleWindowRepository.findByTermIdAndType(TERM_ID, WindowType.GROUP_CREATION))
                .thenReturn(Optional.of(openWindow));

        assertThatThrownBy(() -> groupService.createGroup(GROUP_NAME, STUDENT_ID))
                .isInstanceOf(ScheduleWindowClosedException.class);

        verify(projectGroupRepository, never()).save(any());
    }

    @Test
    void createGroup_studentAlreadyInGroup_throwsAlreadyInGroupException() {
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
        when(scheduleWindowRepository.findByTermIdAndType(TERM_ID, WindowType.GROUP_CREATION))
                .thenReturn(Optional.of(openWindow));
        when(groupMembershipRepository.existsByStudentId(STUDENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> groupService.createGroup(GROUP_NAME, STUDENT_ID))
                .isInstanceOf(AlreadyInGroupException.class)
                .hasMessageContaining("already a member");

        verify(projectGroupRepository, never()).save(any());
    }

    @Test
    void createGroup_duplicateGroupName_throwsDuplicateGroupNameException() {
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
        when(scheduleWindowRepository.findByTermIdAndType(TERM_ID, WindowType.GROUP_CREATION))
                .thenReturn(Optional.of(openWindow));
        when(groupMembershipRepository.existsByStudentId(STUDENT_ID)).thenReturn(false);
        when(projectGroupRepository.existsByGroupNameAndTermId(GROUP_NAME, TERM_ID)).thenReturn(true);

        assertThatThrownBy(() -> groupService.createGroup(GROUP_NAME, STUDENT_ID))
                .isInstanceOf(DuplicateGroupNameException.class)
                .hasMessageContaining(GROUP_NAME);

        verify(projectGroupRepository, never()).save(any());
    }

    // ── getMyGroup ─────────────────────────────────────────────────────────────

    @Test
    void getMyGroup_success_returnsGroupDetailResponse() {
        GroupMembership membership = new GroupMembership();
        membership.setStudent(student);
        membership.setRole(MemberRole.TEAM_LEADER);
        membership.setJoinedAt(LocalDateTime.now());
        membership.setGroup(savedGroup);

        when(groupMembershipRepository.findByStudentId(STUDENT_ID))
                .thenReturn(Optional.of(membership));
        when(projectGroupRepository.findById(savedGroup.getId()))
                .thenReturn(Optional.of(savedGroup));
        when(groupMembershipRepository.findByGroupId(savedGroup.getId()))
                .thenReturn(List.of(membership));

        GroupDetailResponse response = groupService.getMyGroup(STUDENT_ID);

        assertThat(response.getId()).isEqualTo(savedGroup.getId());
        assertThat(response.getGroupName()).isEqualTo(GROUP_NAME);
        assertThat(response.getMembers()).hasSize(1);
        assertThat(response.getMembers().get(0).getRole()).isEqualTo("TEAM_LEADER");
    }

    @Test
    void getMyGroup_noMembership_throwsRuntimeException() {
        when(groupMembershipRepository.findByStudentId(STUDENT_ID))
                .thenReturn(Optional.empty());

        // Currently throws RuntimeException — should be GroupNotFoundException (follow-up fix)
        assertThatThrownBy(() -> groupService.getMyGroup(STUDENT_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not a member");
    }

    // ── getGroupsForActiveTerm ─────────────────────────────────────────────────

    @Nested
    class GetGroupsForActiveTerm {

        @Test
        void returnsGroupDetailResponseListForActiveTerm() {
            when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
            when(projectGroupRepository.findByTermId(TERM_ID)).thenReturn(List.of(savedGroup));
            when(groupMembershipRepository.findByGroupId(savedGroup.getId())).thenReturn(List.of());

            List<GroupDetailResponse> result = groupService.getGroupsForActiveTerm();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(savedGroup.getId());
            assertThat(result.get(0).getGroupName()).isEqualTo(GROUP_NAME);
            assertThat(result.get(0).getTermId()).isEqualTo(TERM_ID);
        }

        @Test
        void returnsEmptyListWhenNoGroupsInActiveTerm() {
            when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
            when(projectGroupRepository.findByTermId(TERM_ID)).thenReturn(List.of());

            List<GroupDetailResponse> result = groupService.getGroupsForActiveTerm();

            assertThat(result).isEmpty();
        }
    }

    // ── getGroupDetail ─────────────────────────────────────────────────────────

    @Nested
    class GetGroupDetail {

        @Test
        void returnsGroupDetailResponseForFoundGroup() {
            UUID groupId = savedGroup.getId();
            when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(savedGroup));
            when(groupMembershipRepository.findByGroupId(groupId)).thenReturn(List.of());

            GroupDetailResponse result = groupService.getGroupDetail(groupId);

            assertThat(result.getId()).isEqualTo(groupId);
            assertThat(result.getGroupName()).isEqualTo(GROUP_NAME);
            assertThat(result.getStatus()).isEqualTo("FORMING");
        }

        @Test
        void throwsGroupNotFoundExceptionWhenGroupMissing() {
            UUID unknownId = UUID.randomUUID();
            when(projectGroupRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupService.getGroupDetail(unknownId))
                    .isInstanceOf(GroupNotFoundException.class)
                    .hasMessageContaining("not found");
        }
    }

    // ── coordinatorAddStudent ──────────────────────────────────────────────────

    @Nested
    class CoordinatorAddStudent {

        private static final String STUDENT_ID_STR = "12345678901";
        private final UUID groupId = UUID.randomUUID();
        private final UUID studentUUID = UUID.randomUUID();

        private ProjectGroup group;
        private Student targetStudent;

        @BeforeEach
        void setUpAdd() {
            group = new ProjectGroup();
            group.setId(groupId);
            group.setGroupName("TargetGroup");
            group.setTermId(TERM_ID);
            group.setStatus(GroupStatus.FORMING);
            group.setCreatedAt(LocalDateTime.now());

            targetStudent = new Student();
            targetStudent.setId(studentUUID);
            targetStudent.setStudentId(STUDENT_ID_STR);
        }

        @Test
        void happyPath_addsMemberAndAutoDeniesInvitations() {
            when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(studentRepository.findByStudentId(STUDENT_ID_STR)).thenReturn(Optional.of(targetStudent));
            when(groupMembershipRepository.existsByStudentId(studentUUID)).thenReturn(false);
            when(groupMembershipRepository.countByGroupId(groupId)).thenReturn(1L);
            when(groupInvitationRepository.countByGroupIdAndStatus(groupId, InvitationStatus.PENDING)).thenReturn(0L);
            when(termConfigService.getMaxTeamSize()).thenReturn(5);
            when(groupMembershipRepository.save(any())).thenReturn(new GroupMembership());
            when(groupMembershipRepository.findByGroupId(groupId)).thenReturn(List.of());

            groupService.coordinatorAddStudent(groupId, STUDENT_ID_STR);

            ArgumentCaptor<GroupMembership> captor = ArgumentCaptor.forClass(GroupMembership.class);
            verify(groupMembershipRepository).save(captor.capture());
            assertThat(captor.getValue().getRole()).isEqualTo(MemberRole.MEMBER);
            assertThat(captor.getValue().getStudent()).isEqualTo(targetStudent);
            assertThat(captor.getValue().getGroup()).isEqualTo(group);

            verify(groupInvitationRepository).autoDenyOtherPendingInvitations(studentUUID, groupId);
        }

        @Test
        void throwsGroupNotFoundWhenGroupMissing() {
            when(projectGroupRepository.findById(groupId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupService.coordinatorAddStudent(groupId, STUDENT_ID_STR))
                    .isInstanceOf(GroupNotFoundException.class);

            verify(groupMembershipRepository, never()).save(any());
        }

        @Test
        void throwsStudentNotFoundWhenStudentMissing() {
            when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(studentRepository.findByStudentId(STUDENT_ID_STR)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupService.coordinatorAddStudent(groupId, STUDENT_ID_STR))
                    .isInstanceOf(StudentNotFoundException.class)
                    .hasMessageContaining(STUDENT_ID_STR);

            verify(groupMembershipRepository, never()).save(any());
        }

        @Test
        void throwsBusinessRuleExceptionWhenStudentAlreadyInGroup() {
            when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(studentRepository.findByStudentId(STUDENT_ID_STR)).thenReturn(Optional.of(targetStudent));
            when(groupMembershipRepository.existsByStudentId(studentUUID)).thenReturn(true);

            assertThatThrownBy(() -> groupService.coordinatorAddStudent(groupId, STUDENT_ID_STR))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already a member");

            verify(groupMembershipRepository, never()).save(any());
        }

        @Test
        void throwsBusinessRuleExceptionWhenGroupAtCapacity_membersAlone() {
            // 5 current members, 0 pending — at hard limit of 5
            when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(studentRepository.findByStudentId(STUDENT_ID_STR)).thenReturn(Optional.of(targetStudent));
            when(groupMembershipRepository.existsByStudentId(studentUUID)).thenReturn(false);
            when(groupMembershipRepository.countByGroupId(groupId)).thenReturn(5L);
            when(groupInvitationRepository.countByGroupIdAndStatus(groupId, InvitationStatus.PENDING)).thenReturn(0L);
            when(termConfigService.getMaxTeamSize()).thenReturn(5);

            assertThatThrownBy(() -> groupService.coordinatorAddStudent(groupId, STUDENT_ID_STR))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("maximum team size");

            verify(groupMembershipRepository, never()).save(any());
        }

        @Test
        void throwsBusinessRuleExceptionWhenGroupAtCapacity_membersAndPendingCombined() {
            // 3 members + 2 pending = 5 = maxTeamSize → at capacity
            when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(studentRepository.findByStudentId(STUDENT_ID_STR)).thenReturn(Optional.of(targetStudent));
            when(groupMembershipRepository.existsByStudentId(studentUUID)).thenReturn(false);
            when(groupMembershipRepository.countByGroupId(groupId)).thenReturn(3L);
            when(groupInvitationRepository.countByGroupIdAndStatus(groupId, InvitationStatus.PENDING)).thenReturn(2L);
            when(termConfigService.getMaxTeamSize()).thenReturn(5);

            assertThatThrownBy(() -> groupService.coordinatorAddStudent(groupId, STUDENT_ID_STR))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("maximum team size");

            verify(groupMembershipRepository, never()).save(any());
        }

        @Test
        void allowsAddWhenBelowCapacity() {
            // 2 members + 2 pending = 4 < 5 → should succeed
            when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(studentRepository.findByStudentId(STUDENT_ID_STR)).thenReturn(Optional.of(targetStudent));
            when(groupMembershipRepository.existsByStudentId(studentUUID)).thenReturn(false);
            when(groupMembershipRepository.countByGroupId(groupId)).thenReturn(2L);
            when(groupInvitationRepository.countByGroupIdAndStatus(groupId, InvitationStatus.PENDING)).thenReturn(2L);
            when(termConfigService.getMaxTeamSize()).thenReturn(5);
            when(groupMembershipRepository.save(any())).thenReturn(new GroupMembership());
            when(groupMembershipRepository.findByGroupId(groupId)).thenReturn(List.of());

            groupService.coordinatorAddStudent(groupId, STUDENT_ID_STR);

            verify(groupMembershipRepository).save(any(GroupMembership.class));
        }
    }

    // ── coordinatorRemoveStudent ───────────────────────────────────────────────

    @Nested
    class CoordinatorRemoveStudent {

        private static final String STUDENT_ID_STR = "98765432100";
        private final UUID groupId = UUID.randomUUID();
        private final UUID studentUUID = UUID.randomUUID();

        private ProjectGroup group;
        private Student targetStudent;

        @BeforeEach
        void setUpRemove() {
            group = new ProjectGroup();
            group.setId(groupId);
            group.setGroupName("RemoveGroup");
            group.setTermId(TERM_ID);
            group.setStatus(GroupStatus.FORMING);
            group.setCreatedAt(LocalDateTime.now());

            targetStudent = new Student();
            targetStudent.setId(studentUUID);
            targetStudent.setStudentId(STUDENT_ID_STR);
        }

        @Test
        void happyPath_deletesMembershipAndReturnsUpdatedGroup() {
            GroupMembership membership = new GroupMembership();
            membership.setStudent(targetStudent);
            membership.setGroup(group);
            membership.setRole(MemberRole.MEMBER);

            when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(studentRepository.findByStudentId(STUDENT_ID_STR)).thenReturn(Optional.of(targetStudent));
            when(groupMembershipRepository.findByGroupIdAndStudentId(groupId, studentUUID))
                    .thenReturn(Optional.of(membership));
            when(groupMembershipRepository.findByGroupId(groupId)).thenReturn(List.of());

            GroupDetailResponse result = groupService.coordinatorRemoveStudent(groupId, STUDENT_ID_STR);

            verify(groupMembershipRepository).delete(membership);
            assertThat(result.getId()).isEqualTo(groupId);
        }

        @Test
        void throwsGroupNotFoundWhenGroupMissing() {
            when(projectGroupRepository.findById(groupId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupService.coordinatorRemoveStudent(groupId, STUDENT_ID_STR))
                    .isInstanceOf(GroupNotFoundException.class);

            verify(groupMembershipRepository, never()).delete(any());
        }

        @Test
        void throwsStudentNotFoundWhenStudentMissing() {
            when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(studentRepository.findByStudentId(STUDENT_ID_STR)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupService.coordinatorRemoveStudent(groupId, STUDENT_ID_STR))
                    .isInstanceOf(StudentNotFoundException.class)
                    .hasMessageContaining(STUDENT_ID_STR);

            verify(groupMembershipRepository, never()).delete(any());
        }

        @Test
        void throwsGroupNotFoundWhenStudentNotMemberOfGroup() {
            when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(studentRepository.findByStudentId(STUDENT_ID_STR)).thenReturn(Optional.of(targetStudent));
            when(groupMembershipRepository.findByGroupIdAndStudentId(groupId, studentUUID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupService.coordinatorRemoveStudent(groupId, STUDENT_ID_STR))
                    .isInstanceOf(GroupNotFoundException.class)
                    .hasMessageContaining("not a member");

            verify(groupMembershipRepository, never()).delete(any());
        }

        @Test
        void throwsForbiddenExceptionWhenRemovingTeamLeader() {
            GroupMembership leaderMembership = new GroupMembership();
            leaderMembership.setStudent(targetStudent);
            leaderMembership.setGroup(group);
            leaderMembership.setRole(MemberRole.TEAM_LEADER);

            when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(studentRepository.findByStudentId(STUDENT_ID_STR)).thenReturn(Optional.of(targetStudent));
            when(groupMembershipRepository.findByGroupIdAndStudentId(groupId, studentUUID))
                    .thenReturn(Optional.of(leaderMembership));

            assertThatThrownBy(() -> groupService.coordinatorRemoveStudent(groupId, STUDENT_ID_STR))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Team Leader");

            verify(groupMembershipRepository, never()).delete(any());
        }
    }

    // ── disbandGroup ───────────────────────────────────────────────────────────

    @Nested
    class DisbandGroup {

        private final UUID groupId = UUID.randomUUID();
        private ProjectGroup activeGroup;

        @BeforeEach
        void setUpDisband() {
            activeGroup = new ProjectGroup();
            activeGroup.setId(groupId);
            activeGroup.setGroupName("DisbandGroup");
            activeGroup.setTermId(TERM_ID);
            activeGroup.setStatus(GroupStatus.FORMING);
            activeGroup.setCreatedAt(LocalDateTime.now());
        }

        @Test
        void happyPath_setsStatusDisbandedAndCascades() {
            when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(activeGroup));
            when(projectGroupRepository.save(any())).thenReturn(activeGroup);
            when(groupMembershipRepository.findByGroupId(groupId)).thenReturn(List.of());

            GroupDetailResponse result = groupService.disbandGroup(groupId);

            // Status updated
            ArgumentCaptor<ProjectGroup> captor = ArgumentCaptor.forClass(ProjectGroup.class);
            verify(projectGroupRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(GroupStatus.DISBANDED);

            // Cascade: memberships hard-deleted
            verify(groupMembershipRepository).deleteByGroupId(groupId);

            // Cascade: pending invitations auto-denied
            verify(groupInvitationRepository).autoDenyAllPendingByGroupId(groupId);

            // Cascade: pending advisor requests auto-rejected (P3 prep)
            verify(advisorRequestRepository).bulkUpdateStatusByGroupId(
                    eq(RequestStatus.AUTO_REJECTED), eq(groupId));

            assertThat(result.getStatus()).isEqualTo("DISBANDED");
        }

        @Test
        void throwsGroupNotFoundWhenGroupMissing() {
            when(projectGroupRepository.findById(groupId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupService.disbandGroup(groupId))
                    .isInstanceOf(GroupNotFoundException.class);

            verify(projectGroupRepository, never()).save(any());
            verify(groupMembershipRepository, never()).deleteByGroupId(any());
        }

        @Test
        void throwsBusinessRuleExceptionWhenAlreadyDisbanded() {
            activeGroup.setStatus(GroupStatus.DISBANDED);
            when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(activeGroup));

            assertThatThrownBy(() -> groupService.disbandGroup(groupId))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already disbanded");

            verify(projectGroupRepository, never()).save(any());
            verify(groupMembershipRepository, never()).deleteByGroupId(any());
        }

        @Test
        void worksForGroupInToolsBoundStatus() {
            activeGroup.setStatus(GroupStatus.TOOLS_BOUND);
            when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(activeGroup));
            when(projectGroupRepository.save(any())).thenReturn(activeGroup);
            when(groupMembershipRepository.findByGroupId(groupId)).thenReturn(List.of());

            groupService.disbandGroup(groupId);

            ArgumentCaptor<ProjectGroup> captor = ArgumentCaptor.forClass(ProjectGroup.class);
            verify(projectGroupRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(GroupStatus.DISBANDED);
        }

        @Test
        void worksForGroupInAdvisorAssignedStatus() {
            activeGroup.setStatus(GroupStatus.ADVISOR_ASSIGNED);
            when(projectGroupRepository.findById(groupId)).thenReturn(Optional.of(activeGroup));
            when(projectGroupRepository.save(any())).thenReturn(activeGroup);
            when(groupMembershipRepository.findByGroupId(groupId)).thenReturn(List.of());

            groupService.disbandGroup(groupId);

            ArgumentCaptor<ProjectGroup> captor = ArgumentCaptor.forClass(ProjectGroup.class);
            verify(projectGroupRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(GroupStatus.DISBANDED);
        }
    }
}
