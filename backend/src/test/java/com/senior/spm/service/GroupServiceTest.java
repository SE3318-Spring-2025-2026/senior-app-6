package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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

import com.senior.spm.controller.dto.BindToolResponse;
import com.senior.spm.controller.dto.GroupDetailResponse;
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
import com.senior.spm.exception.GitHubValidationException;
import com.senior.spm.exception.GroupNotFoundException;
import com.senior.spm.exception.JiraValidationException;
import com.senior.spm.exception.NotInGroupException;
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
    @Mock private JiraValidationService jiraValidationService;
    @Mock private GitHubValidationService gitHubValidationService;
    @Mock private EncryptionService encryptionService;

    @InjectMocks
    private GroupService groupService;

    private static final String TERM_ID = "2024-FALL";
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID GROUP_ID = UUID.randomUUID();
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
        openWindow.setClosesAt(LocalDateTime.now().minusHours(1)); // already closed

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

    @Test
    void getMyGroup_groupEntityMissing_throwsRuntimeException() {
        // Data integrity edge case: membership row exists but ProjectGroup row is missing.
        // Service throws raw RuntimeException("Group not found") — no typed exception.
        GroupMembership membership = new GroupMembership();
        membership.setStudent(student);
        membership.setRole(MemberRole.TEAM_LEADER);
        membership.setJoinedAt(LocalDateTime.now());
        membership.setGroup(savedGroup);

        when(groupMembershipRepository.findByStudentId(STUDENT_ID)).thenReturn(Optional.of(membership));
        when(projectGroupRepository.findById(savedGroup.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.getMyGroup(STUDENT_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Group not found");
    }

    @Test
    void createGroup_studentNotFound_throwsRuntimeException() {
        // Data integrity edge case: JWT UUID has no matching student row.
        // Service throws raw RuntimeException("Student not found") — no typed exception.
        when(termConfigService.getActiveTermId()).thenReturn(TERM_ID);
        when(scheduleWindowRepository.findByTermIdAndType(TERM_ID, WindowType.GROUP_CREATION))
                .thenReturn(Optional.of(openWindow));
        when(groupMembershipRepository.existsByStudentId(STUDENT_ID)).thenReturn(false);
        when(projectGroupRepository.existsByGroupNameAndTermId(GROUP_NAME, TERM_ID)).thenReturn(false);
        when(projectGroupRepository.save(any())).thenReturn(savedGroup);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.createGroup(GROUP_NAME, STUDENT_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Student not found");
    }

    // ── MemberResponse mapping ─────────────────────────────────────────────────

    @Test
    void getMyGroup_memberStudentIdIsEnrollmentString_notUUID() {
        // PR #83 critical fix: MemberResponse.studentId must be the enrollment number
        // (e.g. "23070006036"), NOT the internal UUID. Verifies m.getStudent().getStudentId().
        student.setStudentId("23070006036");

        GroupMembership membership = new GroupMembership();
        membership.setStudent(student);
        membership.setRole(MemberRole.TEAM_LEADER);
        membership.setJoinedAt(LocalDateTime.now());
        membership.setGroup(savedGroup);

        when(groupMembershipRepository.findByStudentId(STUDENT_ID)).thenReturn(Optional.of(membership));
        when(projectGroupRepository.findById(savedGroup.getId())).thenReturn(Optional.of(savedGroup));
        when(groupMembershipRepository.findByGroupId(savedGroup.getId())).thenReturn(List.of(membership));

        GroupDetailResponse response = groupService.getMyGroup(STUDENT_ID);

        assertThat(response.getMembers().get(0).getStudentId()).isEqualTo("23070006036");
        assertThat(response.getMembers().get(0).getStudentId()).isNotEqualTo(STUDENT_ID.toString());
    }

    // ── jiraBound / githubBound derivation ────────────────────────────────────

    @Test
    void getMyGroup_jiraBoundTrue_whenEncryptedTokenPresent() {
        // jiraBound = encryptedJiraToken != null (not just URL presence)
        savedGroup.setJiraSpaceUrl("https://senior.atlassian.net");
        savedGroup.setJiraProjectKey("SPM");
        savedGroup.setEncryptedJiraToken("enc-tok"); // token stored after successful bind

        GroupMembership membership = new GroupMembership();
        membership.setStudent(student);
        membership.setRole(MemberRole.TEAM_LEADER);
        membership.setJoinedAt(LocalDateTime.now());
        membership.setGroup(savedGroup);

        when(groupMembershipRepository.findByStudentId(STUDENT_ID)).thenReturn(Optional.of(membership));
        when(projectGroupRepository.findById(savedGroup.getId())).thenReturn(Optional.of(savedGroup));
        when(groupMembershipRepository.findByGroupId(savedGroup.getId())).thenReturn(List.of(membership));

        GroupDetailResponse response = groupService.getMyGroup(STUDENT_ID);

        assertThat(response.getJiraBound()).isTrue();
        assertThat(response.getJiraSpaceUrl()).isEqualTo("https://senior.atlassian.net");
    }

    @Test
    void getMyGroup_jiraBoundFalseWhenFieldsMissing() {
        // Default: jiraSpaceUrl and jiraProjectKey are null → jiraBound = false
        GroupMembership membership = new GroupMembership();
        membership.setStudent(student);
        membership.setRole(MemberRole.TEAM_LEADER);
        membership.setJoinedAt(LocalDateTime.now());
        membership.setGroup(savedGroup);

        when(groupMembershipRepository.findByStudentId(STUDENT_ID)).thenReturn(Optional.of(membership));
        when(projectGroupRepository.findById(savedGroup.getId())).thenReturn(Optional.of(savedGroup));
        when(groupMembershipRepository.findByGroupId(savedGroup.getId())).thenReturn(List.of(membership));

        GroupDetailResponse response = groupService.getMyGroup(STUDENT_ID);

        assertThat(response.getJiraBound()).isFalse();
    }

    @Test
    void getMyGroup_githubBoundTrue_whenEncryptedPatPresent() {
        // githubBound = encryptedGithubPat != null (not just org name presence)
        savedGroup.setGithubOrgName("senior-team-6");
        savedGroup.setEncryptedGithubPat("enc-pat"); // PAT stored after successful bind

        GroupMembership membership = new GroupMembership();
        membership.setStudent(student);
        membership.setRole(MemberRole.TEAM_LEADER);
        membership.setJoinedAt(LocalDateTime.now());
        membership.setGroup(savedGroup);

        when(groupMembershipRepository.findByStudentId(STUDENT_ID)).thenReturn(Optional.of(membership));
        when(projectGroupRepository.findById(savedGroup.getId())).thenReturn(Optional.of(savedGroup));
        when(groupMembershipRepository.findByGroupId(savedGroup.getId())).thenReturn(List.of(membership));

        GroupDetailResponse response = groupService.getMyGroup(STUDENT_ID);

        assertThat(response.getGithubBound()).isTrue();
    }

    @Test
    void getMyGroup_jiraBoundFalse_whenUrlSetButNoToken() {
        // BUG REGRESSION: URL presence alone must NOT imply jiraBound=true.
        // A partial/failed bind could leave jiraSpaceUrl set without storing a token.
        // Correct logic: jiraBound = encryptedJiraToken != null
        savedGroup.setJiraSpaceUrl("https://senior.atlassian.net");
        savedGroup.setJiraProjectKey("SPM");
        // encryptedJiraToken intentionally NOT set

        GroupMembership membership = new GroupMembership();
        membership.setStudent(student);
        membership.setRole(MemberRole.TEAM_LEADER);
        membership.setJoinedAt(LocalDateTime.now());
        membership.setGroup(savedGroup);

        when(groupMembershipRepository.findByStudentId(STUDENT_ID)).thenReturn(Optional.of(membership));
        when(projectGroupRepository.findById(savedGroup.getId())).thenReturn(Optional.of(savedGroup));
        when(groupMembershipRepository.findByGroupId(savedGroup.getId())).thenReturn(List.of(membership));

        GroupDetailResponse response = groupService.getMyGroup(STUDENT_ID);

        assertThat(response.getJiraBound()).isFalse();
    }

    // ── bindJira ───────────────────────────────────────────────────────────────

    @Test
    void bindJira_success_formingToToolsPending_whenGithubNotBound() {
        // DFD 2.4: first tool bound → status transitions FORMING → TOOLS_PENDING
        savedGroup.setId(GROUP_ID);
        savedGroup.setStatus(GroupStatus.FORMING);

        GroupMembership leader = teamLeaderMembership();
        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(leader));
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(savedGroup));
        when(encryptionService.encrypt("raw-jira-token")).thenReturn("enc-jira-token");
        when(projectGroupRepository.save(any())).thenReturn(savedGroup);

        groupService.bindJira(GROUP_ID, "https://co.atlassian.net", "SPM", "raw-jira-token", STUDENT_ID);

        ArgumentCaptor<ProjectGroup> captor = ArgumentCaptor.forClass(ProjectGroup.class);
        verify(projectGroupRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(GroupStatus.TOOLS_PENDING);
        assertThat(captor.getValue().getEncryptedJiraToken()).isEqualTo("enc-jira-token");
    }

    @Test
    void bindJira_success_toolsPendingToToolsBound_whenGithubAlreadyBound() {
        // DFD 2.4: both tools bound → status transitions to TOOLS_BOUND
        savedGroup.setId(GROUP_ID);
        savedGroup.setStatus(GroupStatus.TOOLS_PENDING);
        savedGroup.setEncryptedGithubPat("enc-github-pat"); // GitHub was bound first

        GroupMembership leader = teamLeaderMembership();
        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(leader));
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(savedGroup));
        when(encryptionService.encrypt(any())).thenReturn("enc-jira-token");
        when(projectGroupRepository.save(any())).thenReturn(savedGroup);

        groupService.bindJira(GROUP_ID, "https://co.atlassian.net", "SPM", "raw-token", STUDENT_ID);

        ArgumentCaptor<ProjectGroup> captor = ArgumentCaptor.forClass(ProjectGroup.class);
        verify(projectGroupRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(GroupStatus.TOOLS_BOUND);
    }

    @Test
    void bindJira_nonTeamLeader_throwsForbiddenException_beforeAnyExternalCall() {
        // Sequence 2.4 step 1: role check fires before any external call
        savedGroup.setId(GROUP_ID);
        GroupMembership member = new GroupMembership();
        member.setStudent(student);
        member.setGroup(savedGroup);
        member.setRole(MemberRole.MEMBER); // NOT leader
        member.setJoinedAt(LocalDateTime.now());

        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> groupService.bindJira(GROUP_ID, "url", "key", "token", STUDENT_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Team Leader");

        verify(jiraValidationService, never()).validate(any(), any(), any());
        verify(projectGroupRepository, never()).save(any());
    }

    @Test
    void bindJira_notInGroup_throwsForbiddenException() {
        savedGroup.setId(GROUP_ID);
        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.bindJira(GROUP_ID, "url", "key", "token", STUDENT_ID))
                .isInstanceOf(ForbiddenException.class);

        verify(jiraValidationService, never()).validate(any(), any(), any());
    }

    @Test
    void bindJira_disbandedGroup_throwsBusinessRuleException() {
        // DISBANDED freeze: must be enforced before any external call (CLAUDE.md P2 rule 1)
        savedGroup.setId(GROUP_ID);
        savedGroup.setStatus(GroupStatus.DISBANDED);

        GroupMembership leader = teamLeaderMembership();
        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(leader));
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(savedGroup));

        assertThatThrownBy(() -> groupService.bindJira(GROUP_ID, "url", "key", "token", STUDENT_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("disbanded");

        verify(jiraValidationService, never()).validate(any(), any(), any());
        verify(projectGroupRepository, never()).save(any());
    }

    @Test
    void bindJira_groupNotFound_throwsGroupNotFoundException() {
        savedGroup.setId(GROUP_ID);
        GroupMembership leader = teamLeaderMembership();
        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(leader));
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.bindJira(GROUP_ID, "url", "key", "token", STUDENT_ID))
                .isInstanceOf(GroupNotFoundException.class);

        verify(projectGroupRepository, never()).save(any());
    }

    @Test
    void bindJira_validationFails_nothingSaved() {
        // DFD 2.4: validate before save — validation failure must not persist anything
        savedGroup.setId(GROUP_ID);
        savedGroup.setStatus(GroupStatus.FORMING);

        GroupMembership leader = teamLeaderMembership();
        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(leader));
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(savedGroup));
        doThrow(new JiraValidationException("JIRA validation failed: API token is invalid or expired"))
                .when(jiraValidationService).validate(any(), any(), any());

        assertThatThrownBy(() -> groupService.bindJira(GROUP_ID, "url", "key", "bad-token", STUDENT_ID))
                .isInstanceOf(JiraValidationException.class);

        verify(encryptionService, never()).encrypt(any());
        verify(projectGroupRepository, never()).save(any());
    }

    @Test
    void bindJira_encryptedTokenStoredNotPlaintext() {
        // NFR-7: token must be encrypted before DB write — plaintext must never reach DB
        savedGroup.setId(GROUP_ID);
        savedGroup.setStatus(GroupStatus.FORMING);

        GroupMembership leader = teamLeaderMembership();
        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(leader));
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(savedGroup));
        when(encryptionService.encrypt("raw-token")).thenReturn("enc-token");
        when(projectGroupRepository.save(any())).thenReturn(savedGroup);

        groupService.bindJira(GROUP_ID, "url", "key", "raw-token", STUDENT_ID);

        ArgumentCaptor<ProjectGroup> captor = ArgumentCaptor.forClass(ProjectGroup.class);
        verify(projectGroupRepository).save(captor.capture());
        assertThat(captor.getValue().getEncryptedJiraToken()).isEqualTo("enc-token");
        assertThat(captor.getValue().getEncryptedJiraToken()).isNotEqualTo("raw-token");
    }

    @Test
    void bindJira_responseNeverContainsPlaintextToken() {
        savedGroup.setId(GROUP_ID);
        savedGroup.setStatus(GroupStatus.FORMING);

        GroupMembership leader = teamLeaderMembership();
        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(leader));
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(savedGroup));
        when(encryptionService.encrypt(any())).thenReturn("enc-token");
        when(projectGroupRepository.save(any())).thenReturn(savedGroup);

        BindToolResponse response = groupService.bindJira(GROUP_ID, "https://co.atlassian.net", "SPM", "raw-token", STUDENT_ID);

        // BindToolResponse has no field for the token — verify no field equals plaintext
        assertThat(response.getJiraSpaceUrl()).isNotEqualTo("raw-token");
        assertThat(response.getJiraProjectKey()).isNotEqualTo("raw-token");
        assertThat(response.getStatus()).isNotEqualTo("raw-token");
    }

    @Test
    void bindJira_rebind_overwritesPreviousToken() {
        savedGroup.setId(GROUP_ID);
        savedGroup.setStatus(GroupStatus.TOOLS_PENDING);
        savedGroup.setEncryptedJiraToken("old-enc-token"); // previously bound

        GroupMembership leader = teamLeaderMembership();
        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(leader));
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(savedGroup));
        when(encryptionService.encrypt("new-raw-token")).thenReturn("new-enc-token");
        when(projectGroupRepository.save(any())).thenReturn(savedGroup);

        groupService.bindJira(GROUP_ID, "url", "key", "new-raw-token", STUDENT_ID);

        ArgumentCaptor<ProjectGroup> captor = ArgumentCaptor.forClass(ProjectGroup.class);
        verify(projectGroupRepository).save(captor.capture());
        assertThat(captor.getValue().getEncryptedJiraToken()).isEqualTo("new-enc-token");
    }

    // ── bindGitHub ─────────────────────────────────────────────────────────────

    @Test
    void bindGitHub_success_formingToToolsPending_whenJiraNotBound() {
        // DFD 2.5: GitHub bound first → status = TOOLS_PENDING
        savedGroup.setId(GROUP_ID);
        savedGroup.setStatus(GroupStatus.FORMING);

        GroupMembership leader = teamLeaderMembership();
        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(leader));
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(savedGroup));
        when(encryptionService.encrypt("raw-pat")).thenReturn("enc-pat");
        when(projectGroupRepository.save(any())).thenReturn(savedGroup);

        groupService.bindGitHub(GROUP_ID, "senior-org", "raw-pat", STUDENT_ID);

        ArgumentCaptor<ProjectGroup> captor = ArgumentCaptor.forClass(ProjectGroup.class);
        verify(projectGroupRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(GroupStatus.TOOLS_PENDING);
        assertThat(captor.getValue().getEncryptedGithubPat()).isEqualTo("enc-pat");
    }

    @Test
    void bindGitHub_success_toolsPendingToToolsBound_whenJiraAlreadyBound() {
        // DFD 2.5: JIRA already bound → binding GitHub completes → TOOLS_BOUND
        savedGroup.setId(GROUP_ID);
        savedGroup.setStatus(GroupStatus.TOOLS_PENDING);
        savedGroup.setEncryptedJiraToken("enc-jira"); // JIRA was bound first

        GroupMembership leader = teamLeaderMembership();
        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(leader));
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(savedGroup));
        when(encryptionService.encrypt(any())).thenReturn("enc-pat");
        when(projectGroupRepository.save(any())).thenReturn(savedGroup);

        groupService.bindGitHub(GROUP_ID, "senior-org", "raw-pat", STUDENT_ID);

        ArgumentCaptor<ProjectGroup> captor = ArgumentCaptor.forClass(ProjectGroup.class);
        verify(projectGroupRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(GroupStatus.TOOLS_BOUND);
    }

    @Test
    void bindGitHub_nonTeamLeader_throwsForbiddenException_beforeAnyExternalCall() {
        savedGroup.setId(GROUP_ID);
        GroupMembership member = new GroupMembership();
        member.setStudent(student);
        member.setGroup(savedGroup);
        member.setRole(MemberRole.MEMBER);
        member.setJoinedAt(LocalDateTime.now());

        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> groupService.bindGitHub(GROUP_ID, "org", "pat", STUDENT_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Team Leader");

        verify(gitHubValidationService, never()).validate(any(), any());
        verify(projectGroupRepository, never()).save(any());
    }

    @Test
    void bindGitHub_disbandedGroup_throwsBusinessRuleException() {
        savedGroup.setId(GROUP_ID);
        savedGroup.setStatus(GroupStatus.DISBANDED);

        GroupMembership leader = teamLeaderMembership();
        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(leader));
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(savedGroup));

        assertThatThrownBy(() -> groupService.bindGitHub(GROUP_ID, "org", "pat", STUDENT_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("disbanded");

        verify(gitHubValidationService, never()).validate(any(), any());
        verify(projectGroupRepository, never()).save(any());
    }

    @Test
    void bindGitHub_validationFails_nothingSaved() {
        savedGroup.setId(GROUP_ID);
        savedGroup.setStatus(GroupStatus.FORMING);

        GroupMembership leader = teamLeaderMembership();
        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(leader));
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(savedGroup));
        doThrow(new GitHubValidationException("GitHub validation failed: PAT is invalid or expired"))
                .when(gitHubValidationService).validate(any(), any());

        assertThatThrownBy(() -> groupService.bindGitHub(GROUP_ID, "org", "bad-pat", STUDENT_ID))
                .isInstanceOf(GitHubValidationException.class);

        verify(encryptionService, never()).encrypt(any());
        verify(projectGroupRepository, never()).save(any());
    }

    @Test
    void bindGitHub_encryptedPatStoredNotPlaintext() {
        savedGroup.setId(GROUP_ID);
        savedGroup.setStatus(GroupStatus.FORMING);

        GroupMembership leader = teamLeaderMembership();
        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(leader));
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(savedGroup));
        when(encryptionService.encrypt("raw-pat")).thenReturn("enc-pat");
        when(projectGroupRepository.save(any())).thenReturn(savedGroup);

        groupService.bindGitHub(GROUP_ID, "senior-org", "raw-pat", STUDENT_ID);

        ArgumentCaptor<ProjectGroup> captor = ArgumentCaptor.forClass(ProjectGroup.class);
        verify(projectGroupRepository).save(captor.capture());
        assertThat(captor.getValue().getEncryptedGithubPat()).isEqualTo("enc-pat");
        assertThat(captor.getValue().getEncryptedGithubPat()).isNotEqualTo("raw-pat");
    }

    @Test
    void bindGitHub_notInGroup_throwsForbiddenException() {
        savedGroup.setId(GROUP_ID);
        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.bindGitHub(GROUP_ID, "org", "pat", STUDENT_ID))
                .isInstanceOf(ForbiddenException.class);

        verify(gitHubValidationService, never()).validate(any(), any());
        verify(projectGroupRepository, never()).save(any());
    }

    @Test
    void bindGitHub_groupNotFound_throwsGroupNotFoundException() {
        savedGroup.setId(GROUP_ID);
        GroupMembership leader = teamLeaderMembership();
        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(leader));
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.bindGitHub(GROUP_ID, "org", "pat", STUDENT_ID))
                .isInstanceOf(GroupNotFoundException.class);

        verify(projectGroupRepository, never()).save(any());
    }

    @Test
    void bindGitHub_responseNeverContainsPlaintextPat() {
        savedGroup.setId(GROUP_ID);
        savedGroup.setStatus(GroupStatus.FORMING);

        GroupMembership leader = teamLeaderMembership();
        when(groupMembershipRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(leader));
        when(projectGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(savedGroup));
        when(encryptionService.encrypt(any())).thenReturn("enc-pat");
        when(projectGroupRepository.save(any())).thenReturn(savedGroup);

        BindToolResponse response = groupService.bindGitHub(GROUP_ID, "senior-org", "raw-pat", STUDENT_ID);

        assertThat(response.getGithubOrgName()).isNotEqualTo("raw-pat");
        assertThat(response.getStatus()).isNotEqualTo("raw-pat");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private GroupMembership teamLeaderMembership() {
        GroupMembership m = new GroupMembership();
        m.setStudent(student);
        m.setGroup(savedGroup);
        m.setRole(MemberRole.TEAM_LEADER);
        m.setJoinedAt(LocalDateTime.now());
        return m;
    }
}
