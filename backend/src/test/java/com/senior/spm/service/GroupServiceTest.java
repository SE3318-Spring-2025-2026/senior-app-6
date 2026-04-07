package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.senior.spm.controller.dto.GroupDetailResponse;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.GroupMembership.MemberRole;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.ScheduleWindow;
import com.senior.spm.entity.ScheduleWindow.WindowType;
import com.senior.spm.entity.Student;
import com.senior.spm.exception.AlreadyInGroupException;
import com.senior.spm.exception.DuplicateGroupNameException;
import com.senior.spm.exception.ScheduleWindowClosedException;
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
        openWindow.setClosesAt(LocalDateTime.now(java.time.ZoneId.of("UTC")).minusHours(1)); // already closed (UTC)

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
}
