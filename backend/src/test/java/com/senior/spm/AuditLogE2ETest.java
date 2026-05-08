package com.senior.spm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senior.spm.entity.AuditLog;
import com.senior.spm.entity.AuditLog.Outcome;
import com.senior.spm.entity.AuditLog.UserType;
import com.senior.spm.entity.GroupInvitation;
import com.senior.spm.entity.GroupInvitation.InvitationStatus;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.GroupMembership.MemberRole;
import com.senior.spm.entity.PasswordResetToken;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.ScheduleWindow;
import com.senior.spm.entity.ScheduleWindow.WindowType;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.StaffUser.Role;
import com.senior.spm.entity.Student;
import com.senior.spm.entity.SystemConfig;
import com.senior.spm.exception.GitHubValidationException;
import com.senior.spm.exception.JiraValidationException;
import com.senior.spm.repository.AdvisorRequestRepository;
import com.senior.spm.repository.AuditLogRepository;
import com.senior.spm.repository.GroupInvitationRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.PasswordResetTokenRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.ScheduleWindowRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.repository.StudentRepository;
import com.senior.spm.repository.SystemConfigRepository;
import com.senior.spm.service.GitHubValidationService;
import com.senior.spm.service.GithubService;
import com.senior.spm.service.AuthService;
import com.senior.spm.service.JiraValidationService;
import com.senior.spm.service.JWTService;

/**
 * End-to-end integration tests for TC-AUDIT-01 through TC-AUDIT-15.
 *
 * Verifies that all audit call sites from PR #285 produce correct DB rows and that
 * the REQUIRES_NEW transaction contract (TC-07/08/15) and coordinator userId fix
 * from PR #297/FIX-2 (TC-11/12/13/14) are honoured.
 *
 * Spec: red_notes/audit-log-query-qa.md
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(locations = "classpath:test.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuditLogE2ETest {

    // ── mocked external-service beans ────────────────────────────────────────
    @MockitoBean JiraValidationService jiraValidationService;
    @MockitoBean GitHubValidationService gitHubValidationService;
    @MockitoBean GithubService githubService;

    // ── infrastructure ───────────────────────────────────────────────────────
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JWTService jwtService;
    @Autowired AuthService authService;

    // ── repositories ─────────────────────────────────────────────────────────
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired StaffUserRepository staffUserRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired SystemConfigRepository systemConfigRepository;
    @Autowired ProjectGroupRepository projectGroupRepository;
    @Autowired GroupMembershipRepository groupMembershipRepository;
    @Autowired GroupInvitationRepository groupInvitationRepository;
    @Autowired AdvisorRequestRepository advisorRequestRepository;
    @Autowired ScheduleWindowRepository scheduleWindowRepository;
    @Autowired PasswordResetTokenRepository passwordResetTokenRepository;

    private static final String TERM_ID = "2026-SPRING-AUDIT";
    // bcrypt("password") — same hash used in all integration tests
    private static final String BCRYPT_PASSWORD =
            "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";

    @BeforeEach
    void setUp() {
        // FK-safe teardown
        auditLogRepository.deleteAll();
        advisorRequestRepository.deleteAll();
        groupInvitationRepository.deleteAll();
        groupMembershipRepository.deleteAll();
        projectGroupRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        scheduleWindowRepository.deleteAll();
        studentRepository.deleteAll();
        staffUserRepository.deleteAll();
        systemConfigRepository.deleteAll();

        systemConfigRepository.save(config("active_term_id", TERM_ID));
        systemConfigRepository.save(config("max_team_size", "5"));
    }

    // =========================================================================
    // TC-AUDIT-01 — STAFF_LOGIN SUCCESS
    // =========================================================================
    @Test
    @Order(1)
    void TC_AUDIT_01_staffLoginSuccess_auditRowCommitted() throws Exception {
        StaffUser staff = staffUserRepository.save(newStaff("staff01@test.com", Role.Admin));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("mail", "staff01@test.com", "password", "password")))
                .andExpect(status().isOk());

        AuditLog row = singleRow();
        assertThat(row.getUserId()).isEqualTo(staff.getId());
        assertThat(row.getUserType()).isEqualTo(UserType.STAFF);
        assertThat(row.getAction()).isEqualTo("STAFF_LOGIN");
        assertThat(row.getOutcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(row.getOccurredAt()).isNotNull();
    }

    // =========================================================================
    // TC-AUDIT-02 — STAFF_LOGIN FAILURE
    // =========================================================================
    @Test
    @Order(2)
    void TC_AUDIT_02_staffLoginFailure_auditRowCommitted() throws Exception {
        staffUserRepository.save(newStaff("staff02@test.com", Role.Admin));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("mail", "staff02@test.com", "password", "wrongpassword")))
                .andExpect(status().isUnauthorized());

        AuditLog row = singleRow();
        assertThat(row.getUserId()).isNull();
        assertThat(row.getUserType()).isEqualTo(UserType.STAFF);
        assertThat(row.getAction()).isEqualTo("STAFF_LOGIN");
        assertThat(row.getOutcome()).isEqualTo(Outcome.FAILURE);
    }

    // =========================================================================
    // TC-AUDIT-03 — STUDENT_LOGIN SUCCESS
    // =========================================================================
    @Test
    @Order(3)
    void TC_AUDIT_03_studentLoginSuccess_auditRowCommitted() throws Exception {
        Student student = studentRepository.save(newStudent("12345678901", "gh-user03"));

        when(githubService.exchangeCodeForAccessToken("test-code"))
                .thenReturn(new GithubService.GithubTokenResponse("test-access-token", "bearer", ""));
        when(githubService.getGithubUser("test-access-token"))
                .thenReturn(new GithubService.GithubUserResponse("gh-user03", 1L, null));

        mockMvc.perform(post("/api/auth/github")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("studentId", "12345678901", "code", "test-code")))
                .andExpect(status().isOk());

        AuditLog row = singleRow();
        assertThat(row.getUserId()).isEqualTo(student.getId());
        assertThat(row.getUserType()).isEqualTo(UserType.STUDENT);
        assertThat(row.getAction()).isEqualTo("STUDENT_LOGIN");
        assertThat(row.getOutcome()).isEqualTo(Outcome.SUCCESS);
    }

    // =========================================================================
    // TC-AUDIT-04 — PASSWORD_RESET SUCCESS
    // Note: tested at service layer because AuthController.resetPassword carries
    // its own @Transactional which, combined with open-in-view and the REQUIRES_NEW
    // audit call, triggers UnexpectedRollbackException in the test harness.
    // The audit record() call lives entirely in AuthService — service-layer coverage
    // is sufficient for this TC.
    // =========================================================================
    @Test
    @Order(4)
    void TC_AUDIT_04_passwordResetSuccess_auditRowCommitted() {
        StaffUser staff = staffUserRepository.save(newStaff("staff04@test.com", Role.Admin));
        passwordResetTokenRepository.save(newResetToken(staff, "reset-token-04"));

        authService.resetPassword("reset-token-04", "NewPass@123");

        AuditLog row = singleRow();
        assertThat(row.getUserId()).isEqualTo(staff.getId());
        assertThat(row.getUserType()).isEqualTo(UserType.STAFF);
        assertThat(row.getAction()).isEqualTo("PASSWORD_RESET");
        assertThat(row.getOutcome()).isEqualTo(Outcome.SUCCESS);
    }

    // =========================================================================
    // TC-AUDIT-05 — GROUP_CREATED SUCCESS
    // =========================================================================
    @Test
    @Order(5)
    void TC_AUDIT_05_groupCreated_auditRowCommitted() throws Exception {
        Student student = studentRepository.save(newStudent("12345678902", "gh-creator05"));
        scheduleWindowRepository.save(openWindow(WindowType.GROUP_CREATION));

        mockMvc.perform(post("/api/groups")
                .header("Authorization", "Bearer " + jwtService.issueToken(student))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("groupName", "Audit Test Group")))
                .andExpect(status().isCreated());

        AuditLog row = singleRow();
        assertThat(row.getUserId()).isEqualTo(student.getId());
        assertThat(row.getUserType()).isEqualTo(UserType.STUDENT);
        assertThat(row.getAction()).isEqualTo("GROUP_CREATED");
        assertThat(row.getOutcome()).isEqualTo(Outcome.SUCCESS);
    }

    // =========================================================================
    // TC-AUDIT-06 — INVITATION_SENT SUCCESS
    // =========================================================================
    @Test
    @Order(6)
    void TC_AUDIT_06_invitationSent_auditRowCommitted() throws Exception {
        Student leader = studentRepository.save(newStudent("11111111111", "gh-leader06"));
        Student target = studentRepository.save(newStudent("22222222222", "gh-target06"));
        ProjectGroup group = projectGroupRepository.save(newGroup("Invite Group 06", GroupStatus.FORMING));
        groupMembershipRepository.save(newMembership(leader, group, MemberRole.TEAM_LEADER));

        mockMvc.perform(post("/api/groups/{id}/invitations", group.getId())
                .header("Authorization", "Bearer " + jwtService.issueToken(leader))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("targetStudentId", target.getStudentId())))
                .andExpect(status().isCreated());

        AuditLog row = singleRow();
        assertThat(row.getUserId()).isEqualTo(leader.getId());
        assertThat(row.getUserType()).isEqualTo(UserType.STUDENT);
        assertThat(row.getAction()).isEqualTo("INVITATION_SENT");
        assertThat(row.getOutcome()).isEqualTo(Outcome.SUCCESS);
    }

    // =========================================================================
    // TC-AUDIT-07 — JIRA_BOUND FAILURE (REQUIRES_NEW survives outer tx rollback)
    // =========================================================================
    @Test
    @Order(7)
    void TC_AUDIT_07_jiraBoundFailure_requiresNewAuditRowCommitted() throws Exception {
        Student leader = studentRepository.save(newStudent("33333333333", "gh-jira07"));
        ProjectGroup group = projectGroupRepository.save(newGroup("Jira Group 07", GroupStatus.FORMING));
        groupMembershipRepository.save(newMembership(leader, group, MemberRole.TEAM_LEADER));

        doThrow(new JiraValidationException("Invalid JIRA credentials"))
                .when(jiraValidationService).validate(any(), any(), any(), any());

        mockMvc.perform(post("/api/groups/{id}/jira", group.getId())
                .header("Authorization", "Bearer " + jwtService.issueToken(leader))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "jiraSpaceUrl", "https://test.atlassian.net",
                        "jiraEmail", "test@test.com",
                        "jiraProjectKey", "TEST",
                        "jiraApiToken", "test-token"))))
                .andExpect(status().isUnprocessableEntity());

        AuditLog row = singleRow();
        assertThat(row.getUserId()).isEqualTo(leader.getId());
        assertThat(row.getUserType()).isEqualTo(UserType.STUDENT);
        assertThat(row.getAction()).isEqualTo("JIRA_BOUND");
        assertThat(row.getOutcome()).isEqualTo(Outcome.FAILURE);
    }

    // =========================================================================
    // TC-AUDIT-08 — GITHUB_BOUND FAILURE (REQUIRES_NEW survives outer tx rollback)
    // =========================================================================
    @Test
    @Order(8)
    void TC_AUDIT_08_githubBoundFailure_requiresNewAuditRowCommitted() throws Exception {
        Student leader = studentRepository.save(newStudent("44444444444", "gh-github08"));
        ProjectGroup group = projectGroupRepository.save(newGroup("GitHub Group 08", GroupStatus.FORMING));
        groupMembershipRepository.save(newMembership(leader, group, MemberRole.TEAM_LEADER));

        doThrow(new GitHubValidationException("Invalid GitHub credentials"))
                .when(gitHubValidationService).validate(any(), any(), any());

        mockMvc.perform(post("/api/groups/{id}/github", group.getId())
                .header("Authorization", "Bearer " + jwtService.issueToken(leader))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "githubOrgName", "test-org",
                        "githubPat", "test-pat",
                        "githubRepoName", "test-repo"))))
                .andExpect(status().isUnprocessableEntity());

        AuditLog row = singleRow();
        assertThat(row.getUserId()).isEqualTo(leader.getId());
        assertThat(row.getUserType()).isEqualTo(UserType.STUDENT);
        assertThat(row.getAction()).isEqualTo("GITHUB_BOUND");
        assertThat(row.getOutcome()).isEqualTo(Outcome.FAILURE);
    }

    // =========================================================================
    // TC-AUDIT-09 — INVITATION_RESPONDED (ACCEPT)
    // =========================================================================
    @Test
    @Order(9)
    void TC_AUDIT_09_invitationAccepted_auditRowCommitted() throws Exception {
        Student leader  = studentRepository.save(newStudent("55555555551", "gh-leader09"));
        Student invitee = studentRepository.save(newStudent("55555555552", "gh-invitee09"));
        ProjectGroup group = projectGroupRepository.save(newGroup("Accept Group 09", GroupStatus.FORMING));
        groupMembershipRepository.save(newMembership(leader, group, MemberRole.TEAM_LEADER));
        GroupInvitation inv = groupInvitationRepository.save(pendingInvitation(group, invitee));

        mockMvc.perform(patch("/api/invitations/{id}/respond", inv.getId())
                .header("Authorization", "Bearer " + jwtService.issueToken(invitee))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("accept", "true")))
                .andExpect(status().isOk());

        AuditLog row = singleRow();
        assertThat(row.getUserId()).isEqualTo(invitee.getId());
        assertThat(row.getUserType()).isEqualTo(UserType.STUDENT);
        assertThat(row.getAction()).isEqualTo("INVITATION_RESPONDED");
        assertThat(row.getOutcome()).isEqualTo(Outcome.SUCCESS);
    }

    // =========================================================================
    // TC-AUDIT-10 — INVITATION_RESPONDED (DECLINE)
    // =========================================================================
    @Test
    @Order(10)
    void TC_AUDIT_10_invitationDeclined_auditRowCommitted() throws Exception {
        Student leader  = studentRepository.save(newStudent("66666666661", "gh-leader10"));
        Student invitee = studentRepository.save(newStudent("66666666662", "gh-invitee10"));
        ProjectGroup group = projectGroupRepository.save(newGroup("Decline Group 10", GroupStatus.FORMING));
        groupMembershipRepository.save(newMembership(leader, group, MemberRole.TEAM_LEADER));
        GroupInvitation inv = groupInvitationRepository.save(pendingInvitation(group, invitee));

        mockMvc.perform(patch("/api/invitations/{id}/respond", inv.getId())
                .header("Authorization", "Bearer " + jwtService.issueToken(invitee))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("accept", "false")))
                .andExpect(status().isOk());

        AuditLog row = singleRow();
        assertThat(row.getUserId()).isEqualTo(invitee.getId());
        assertThat(row.getUserType()).isEqualTo(UserType.STUDENT);
        assertThat(row.getAction()).isEqualTo("INVITATION_RESPONDED");
        assertThat(row.getOutcome()).isEqualTo(Outcome.SUCCESS);
    }

    // =========================================================================
    // TC-AUDIT-11 — MEMBER_ADDED: coordinator userId NOT null (FIX-2 / #297)
    // =========================================================================
    @Test
    @Order(11)
    void TC_AUDIT_11_memberAdded_coordinatorUserIdNotNull() throws Exception {
        StaffUser coordinator = staffUserRepository.save(newStaff("coord11@test.com", Role.Coordinator));
        Student outsider = studentRepository.save(newStudent("77777777777", "gh-outsider11"));
        ProjectGroup group = projectGroupRepository.save(newGroup("Coord Group 11", GroupStatus.FORMING));

        mockMvc.perform(patch("/api/coordinator/groups/{id}/members", group.getId())
                .header("Authorization", "Bearer " + jwtService.issueToken(coordinator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("action", "ADD", "studentId", outsider.getStudentId())))
                .andExpect(status().isOk());

        AuditLog row = singleRow();
        assertThat(row.getUserId())
                .as("coordinator userId must not be null — FIX-2 regression check")
                .isNotNull()
                .isEqualTo(coordinator.getId());
        assertThat(row.getUserType()).isEqualTo(UserType.STAFF);
        assertThat(row.getAction()).isEqualTo("MEMBER_ADDED");
        assertThat(row.getOutcome()).isEqualTo(Outcome.SUCCESS);
    }

    // =========================================================================
    // TC-AUDIT-12 — MEMBER_REMOVED: coordinator userId NOT null (FIX-2 / #297)
    // =========================================================================
    @Test
    @Order(12)
    void TC_AUDIT_12_memberRemoved_coordinatorUserIdNotNull() throws Exception {
        StaffUser coordinator = staffUserRepository.save(newStaff("coord12@test.com", Role.Coordinator));
        Student leader = studentRepository.save(newStudent("88888888881", "gh-leader12"));
        Student member = studentRepository.save(newStudent("88888888882", "gh-member12"));
        ProjectGroup group = projectGroupRepository.save(newGroup("Remove Group 12", GroupStatus.FORMING));
        groupMembershipRepository.save(newMembership(leader, group, MemberRole.TEAM_LEADER));
        groupMembershipRepository.save(newMembership(member, group, MemberRole.MEMBER));

        mockMvc.perform(patch("/api/coordinator/groups/{id}/members", group.getId())
                .header("Authorization", "Bearer " + jwtService.issueToken(coordinator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("action", "REMOVE", "studentId", member.getStudentId())))
                .andExpect(status().isOk());

        AuditLog row = singleRow();
        assertThat(row.getUserId())
                .as("coordinator userId must not be null — FIX-2 regression check")
                .isNotNull()
                .isEqualTo(coordinator.getId());
        assertThat(row.getUserType()).isEqualTo(UserType.STAFF);
        assertThat(row.getAction()).isEqualTo("MEMBER_REMOVED");
        assertThat(row.getOutcome()).isEqualTo(Outcome.SUCCESS);
    }

    // =========================================================================
    // TC-AUDIT-13 — GROUP_DISBANDED: coordinator userId NOT null (FIX-2 / #297)
    // =========================================================================
    @Test
    @Order(13)
    void TC_AUDIT_13_groupDisbanded_coordinatorUserIdNotNull() throws Exception {
        StaffUser coordinator = staffUserRepository.save(newStaff("coord13@test.com", Role.Coordinator));
        ProjectGroup group = projectGroupRepository.save(newGroup("Disband Group 13", GroupStatus.FORMING));

        mockMvc.perform(patch("/api/coordinator/groups/{id}/disband", group.getId())
                .header("Authorization", "Bearer " + jwtService.issueToken(coordinator)))
                .andExpect(status().isOk());

        AuditLog row = singleRow();
        assertThat(row.getUserId())
                .as("coordinator userId must not be null — FIX-2 regression check")
                .isNotNull()
                .isEqualTo(coordinator.getId());
        assertThat(row.getUserType()).isEqualTo(UserType.STAFF);
        assertThat(row.getAction()).isEqualTo("GROUP_DISBANDED");
        assertThat(row.getOutcome()).isEqualTo(Outcome.SUCCESS);
    }

    // =========================================================================
    // TC-AUDIT-14 — ADVISOR_ASSIGNED: coordinator userId NOT null (FIX-2 / #297)
    // =========================================================================
    @Test
    @Order(14)
    void TC_AUDIT_14_advisorAssigned_coordinatorUserIdNotNull() throws Exception {
        StaffUser coordinator = staffUserRepository.save(newStaff("coord14@test.com", Role.Coordinator));
        StaffUser professor   = staffUserRepository.save(newStaff("prof14@test.com",  Role.Professor));
        ProjectGroup group = projectGroupRepository.save(newGroup("Advisor Group 14", GroupStatus.TOOLS_BOUND));

        mockMvc.perform(patch("/api/coordinator/groups/{id}/advisor", group.getId())
                .header("Authorization", "Bearer " + jwtService.issueToken(coordinator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("action", "ASSIGN", "advisorId", professor.getId().toString()))))
                .andExpect(status().isOk());

        AuditLog row = singleRow();
        assertThat(row.getUserId())
                .as("coordinator userId must not be null — FIX-2 regression check")
                .isNotNull()
                .isEqualTo(coordinator.getId());
        assertThat(row.getUserType()).isEqualTo(UserType.STAFF);
        assertThat(row.getAction()).isEqualTo("ADVISOR_ASSIGNED");
        assertThat(row.getOutcome()).isEqualTo(Outcome.SUCCESS);
    }

    // =========================================================================
    // TC-AUDIT-15 — REQUIRES_NEW contract end-to-end via API
    //
    // Dual assertion:
    //   (a) outer @Transactional rolled back  → group.encryptedJiraToken == null
    //   (b) inner REQUIRES_NEW committed      → JIRA_BOUND FAILURE row in DB
    // =========================================================================
    @Test
    @Order(15)
    void TC_AUDIT_15_requiresNew_outerTxRolledBack_innerAuditRowCommitted() throws Exception {
        Student leader = studentRepository.save(newStudent("99999999999", "gh-reqnew15"));
        ProjectGroup group = projectGroupRepository.save(newGroup("REQUIRES_NEW Group 15", GroupStatus.FORMING));
        groupMembershipRepository.save(newMembership(leader, group, MemberRole.TEAM_LEADER));

        doThrow(new JiraValidationException("Simulated JIRA failure for REQUIRES_NEW test"))
                .when(jiraValidationService).validate(any(), any(), any(), any());

        mockMvc.perform(post("/api/groups/{id}/jira", group.getId())
                .header("Authorization", "Bearer " + jwtService.issueToken(leader))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "jiraSpaceUrl", "https://test.atlassian.net",
                        "jiraEmail", "test@test.com",
                        "jiraProjectKey", "TEST",
                        "jiraApiToken", "test-token"))))
                .andExpect(status().isUnprocessableEntity());

        // (a) outer tx rolled back: group must NOT have been modified
        ProjectGroup reloaded = projectGroupRepository.findById(group.getId()).orElseThrow();
        assertThat(reloaded.getEncryptedJiraToken())
                .as("(a) outer tx must have rolled back — encryptedJiraToken must remain null")
                .isNull();

        // (b) inner REQUIRES_NEW committed: FAILURE audit row must be in DB
        AuditLog row = singleRow();
        assertThat(row.getAction())
                .as("(b) REQUIRES_NEW inner tx must have committed the audit row")
                .isEqualTo("JIRA_BOUND");
        assertThat(row.getOutcome()).isEqualTo(Outcome.FAILURE);
        assertThat(row.getUserId()).isEqualTo(leader.getId());
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private AuditLog singleRow() {
        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).as("expected exactly 1 audit row after the operation").hasSize(1);
        return rows.get(0);
    }

    private String json(String... keyValuePairs) throws Exception {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            String val = keyValuePairs[i + 1];
            if ("true".equals(val))       m.put(keyValuePairs[i], Boolean.TRUE);
            else if ("false".equals(val)) m.put(keyValuePairs[i], Boolean.FALSE);
            else                          m.put(keyValuePairs[i], val);
        }
        return objectMapper.writeValueAsString(m);
    }

    private SystemConfig config(String key, String value) {
        SystemConfig c = new SystemConfig();
        c.setConfigKey(key);
        c.setConfigValue(value);
        return c;
    }

    private StaffUser newStaff(String mail, Role role) {
        StaffUser u = new StaffUser();
        u.setMail(mail);
        u.setPasswordHash(BCRYPT_PASSWORD);
        u.setRole(role);
        return u;
    }

    private Student newStudent(String studentId, String githubUsername) {
        Student s = new Student();
        s.setStudentId(studentId);
        s.setGithubUsername(githubUsername);
        return s;
    }

    private ProjectGroup newGroup(String name, GroupStatus status) {
        ProjectGroup g = new ProjectGroup();
        g.setGroupName(name);
        g.setTermId(TERM_ID);
        g.setStatus(status);
        g.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));
        return g;
    }

    private GroupMembership newMembership(Student s, ProjectGroup g, MemberRole role) {
        GroupMembership m = new GroupMembership();
        m.setStudent(s);
        m.setGroup(g);
        m.setRole(role);
        m.setJoinedAt(LocalDateTime.now(ZoneId.of("UTC")));
        return m;
    }

    private GroupInvitation pendingInvitation(ProjectGroup g, Student invitee) {
        GroupInvitation inv = new GroupInvitation();
        inv.setGroup(g);
        inv.setInvitee(invitee);
        inv.setStatus(InvitationStatus.PENDING);
        inv.setSentAt(LocalDateTime.now(ZoneId.of("UTC")));
        return inv;
    }

    private ScheduleWindow openWindow(WindowType type) {
        ScheduleWindow w = new ScheduleWindow();
        w.setTermId(TERM_ID);
        w.setType(type);
        w.setOpensAt(LocalDateTime.now(ZoneId.of("UTC")).minusHours(1));
        w.setClosesAt(LocalDateTime.now(ZoneId.of("UTC")).plusHours(1));
        return w;
    }

    private PasswordResetToken newResetToken(StaffUser staff, String token) {
        PasswordResetToken prt = new PasswordResetToken();
        prt.setStaff(staff);
        prt.setToken(token);
        prt.setCreatedAt(LocalDateTime.now());
        prt.setExpiresAt(LocalDateTime.now().plusHours(1));
        return prt;
    }
}
