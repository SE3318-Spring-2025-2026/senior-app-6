package com.senior.spm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senior.spm.entity.GroupInvitation;
import com.senior.spm.entity.GroupInvitation.InvitationStatus;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.GroupMembership.MemberRole;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.StaffUser.Role;
import com.senior.spm.entity.Student;
import com.senior.spm.entity.SystemConfig;
import com.senior.spm.repository.AdvisorRequestRepository;
import com.senior.spm.repository.GroupInvitationRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.repository.StudentRepository;
import com.senior.spm.repository.SystemConfigRepository;
import com.senior.spm.service.JWTService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Black-box integration tests for coordinator group override operations (DFD 2.6).
 *
 * All 15 tests pass green against the full Spring Boot context and H2 in-memory DB.
 *
 * Endpoints covered:
 *   PATCH /api/coordinator/groups/{groupId}/members  (ADD / REMOVE)
 *   PATCH /api/coordinator/groups/{groupId}/disband
 *
 * Acceptance criteria verified:
 *   - Disband confirms DB cascading: memberships hard-deleted, outbound invitations AUTO_DENIED.
 *   - Non-Coordinator roles (Professor, Admin) receive strict 403.
 *   - DB query confirms AUTO_DENIED on all competing invitations after force-add.
 *   - Force-add at capacity returns 400.
 *
 * References: Process 2 / DFD 2.6
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(locations = "classpath:test.properties")
class CoordinatorGroupOverrideIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JWTService jwtService;

    @Autowired StaffUserRepository staffUserRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired SystemConfigRepository systemConfigRepository;
    @Autowired ProjectGroupRepository projectGroupRepository;
    @Autowired GroupMembershipRepository groupMembershipRepository;
    @Autowired GroupInvitationRepository groupInvitationRepository;
    @Autowired AdvisorRequestRepository advisorRequestRepository;

    private static final String TERM_ID = "2026-SPRING-TEST";
    private static final int MAX_TEAM_SIZE = 5;

    private StaffUser coordinator;
    private StaffUser professor;
    private StaffUser admin;

    private Student teamLeader;
    private Student member;
    private Student outsider; // free student — not in any group

    private ProjectGroup group;

    private String coordinatorToken;
    private String professorToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        // FK-safe teardown
        advisorRequestRepository.deleteAll();
        groupInvitationRepository.deleteAll();
        groupMembershipRepository.deleteAll();
        projectGroupRepository.deleteAll();
        studentRepository.deleteAll();
        staffUserRepository.deleteAll();
        systemConfigRepository.deleteAll();

        // System config
        systemConfigRepository.save(config("active_term_id", TERM_ID));
        systemConfigRepository.save(config("max_team_size", String.valueOf(MAX_TEAM_SIZE)));

        // Staff users
        coordinator = staffUserRepository.save(newStaff("coord@test.com", Role.Coordinator));
        professor   = staffUserRepository.save(newStaff("prof@test.com",  Role.Professor));
        admin       = staffUserRepository.save(newStaff("admin@test.com", Role.Admin));

        // Students
        teamLeader = studentRepository.save(newStudent("12345678901", "gh-leader"));
        member     = studentRepository.save(newStudent("12345678902", "gh-member"));
        outsider   = studentRepository.save(newStudent("12345678903", "gh-outsider"));

        // Group with two members (teamLeader + member)
        group = projectGroupRepository.save(newGroup("Alpha Team", GroupStatus.FORMING));
        groupMembershipRepository.save(newMembership(teamLeader, group, MemberRole.TEAM_LEADER));
        groupMembershipRepository.save(newMembership(member, group, MemberRole.MEMBER));

        // Tokens
        coordinatorToken = jwtService.issueToken(coordinator);
        professorToken   = jwtService.issueToken(professor);
        adminToken       = jwtService.issueToken(admin);
    }

    // =========================================================================
    // PATCH /api/coordinator/groups/{groupId}/members — ADD
    // =========================================================================

    /** Test 1 — successful force-add returns 200 and student is persisted in group */
    @Test
    @Order(1)
    void forceAdd_validStudent_returns200AndMembershipCreated() throws Exception {
        mockMvc.perform(patch("/api/coordinator/groups/{id}/members", group.getId())
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(memberBody("ADD", outsider.getStudentId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(group.getId().toString()))
                .andExpect(jsonPath("$.groupName").value("Alpha Team"));

        assertThat(groupMembershipRepository.countByGroupId(group.getId())).isEqualTo(3);
    }

    /**
     * Test 2 — force-add auto-denies all PENDING invitations addressed to the added student.
     * Direct DB query confirms no PENDING rows remain for that student.
     */
    @Test
    @Order(2)
    void forceAdd_autoDeniesAllPendingInvitationsForAddedStudent() throws Exception {
        // outsider has two PENDING invitations from two other groups
        ProjectGroup beta  = projectGroupRepository.save(newGroup("Beta Team",  GroupStatus.FORMING));
        ProjectGroup gamma = projectGroupRepository.save(newGroup("Gamma Team", GroupStatus.FORMING));
        groupInvitationRepository.save(pendingInvitation(beta,  outsider));
        groupInvitationRepository.save(pendingInvitation(gamma, outsider));

        mockMvc.perform(patch("/api/coordinator/groups/{id}/members", group.getId())
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(memberBody("ADD", outsider.getStudentId())))
                .andExpect(status().isOk());

        // DB: every invitation targeting outsider must be AUTO_DENIED — none may remain PENDING
        List<GroupInvitation> invitations = groupInvitationRepository.findByInviteeId(outsider.getId());
        assertThat(invitations).isNotEmpty();
        assertThat(invitations).allMatch(i -> i.getStatus() == InvitationStatus.AUTO_DENIED);
    }

    /**
     * Test 3 — force-add returns 400 when group is at max capacity.
     * Capacity = currentMembers (2) + pendingInvitations (3) = MAX_TEAM_SIZE (5).
     */
    @Test
    @Order(3)
    void forceAdd_groupAtMaxCapacity_returns400() throws Exception {
        // currentMembers=2; need (MAX_TEAM_SIZE - 2) pending invitations to hit the cap
        for (int i = 4; i < 4 + (MAX_TEAM_SIZE - 2); i++) {
            Student filler = studentRepository.save(newStudent("1234567890" + i, "gh-filler" + i));
            groupInvitationRepository.save(pendingInvitation(group, filler));
        }

        mockMvc.perform(patch("/api/coordinator/groups/{id}/members", group.getId())
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(memberBody("ADD", outsider.getStudentId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    /** Test 4 — force-add a student already in a group returns 400 */
    @Test
    @Order(4)
    void forceAdd_studentAlreadyInGroup_returns400() throws Exception {
        mockMvc.perform(patch("/api/coordinator/groups/{id}/members", group.getId())
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(memberBody("ADD", member.getStudentId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    // =========================================================================
    // PATCH /api/coordinator/groups/{groupId}/members — REMOVE
    // =========================================================================

    /** Test 5 — successful force-remove of a regular MEMBER returns 200 */
    @Test
    @Order(5)
    void forceRemove_regularMember_returns200AndMembershipDeleted() throws Exception {
        mockMvc.perform(patch("/api/coordinator/groups/{id}/members", group.getId())
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(memberBody("REMOVE", member.getStudentId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(group.getId().toString()));

        assertThat(groupMembershipRepository.countByGroupId(group.getId())).isEqualTo(1);
    }

    /**
     * Test 6 — TEAM_LEADER removal is blocked with 400.
     * Leadership must be transferred before the leader can be removed.
     * DB confirms the TEAM_LEADER is still in the group.
     */
    @Test
    @Order(6)
    void forceRemove_teamLeader_returns400AndLeaderRemainsInGroup() throws Exception {
        mockMvc.perform(patch("/api/coordinator/groups/{id}/members", group.getId())
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(memberBody("REMOVE", teamLeader.getStudentId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        // DB: count unchanged — TEAM_LEADER was not removed
        assertThat(groupMembershipRepository.countByGroupId(group.getId())).isEqualTo(2);
    }

    /** Test 7 — removing a student not in the group returns 404 */
    @Test
    @Order(7)
    void forceRemove_studentNotInGroup_returns404() throws Exception {
        mockMvc.perform(patch("/api/coordinator/groups/{id}/members", group.getId())
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(memberBody("REMOVE", outsider.getStudentId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    // =========================================================================
    // PATCH /api/coordinator/groups/{groupId}/disband
    // =========================================================================

    /**
     * Test 8 — successful disband returns 200, sets group to DISBANDED, hard-deletes all
     * memberships, and auto-denies all PENDING outbound invitations.
     * DB assertions confirm full cascade.
     */
    @Test
    @Order(8)
    void disband_success_returns200AndCascadesInDb() throws Exception {
        // One PENDING outbound invitation from the group
        groupInvitationRepository.save(pendingInvitation(group, outsider));

        mockMvc.perform(patch("/api/coordinator/groups/{id}/disband", group.getId())
                .header("Authorization", "Bearer " + coordinatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(group.getId().toString()))
                .andExpect(jsonPath("$.status").value("DISBANDED"));

        // DB: group status
        ProjectGroup disbanded = projectGroupRepository.findById(group.getId()).orElseThrow();
        assertThat(disbanded.getStatus()).isEqualTo(GroupStatus.DISBANDED);

        // DB: memberships hard-deleted
        assertThat(groupMembershipRepository.countByGroupId(group.getId())).isZero();

        // DB: outbound invitations AUTO_DENIED
        List<GroupInvitation> invitations = groupInvitationRepository.findByGroupId(group.getId());
        assertThat(invitations).isNotEmpty();
        assertThat(invitations).allMatch(i -> i.getStatus() == InvitationStatus.AUTO_DENIED);
    }

    /** Test 9 — disbanding an already-disbanded group returns 400 */
    @Test
    @Order(9)
    void disband_alreadyDisbanded_returns400() throws Exception {
        group.setStatus(GroupStatus.DISBANDED);
        projectGroupRepository.save(group);

        mockMvc.perform(patch("/api/coordinator/groups/{id}/disband", group.getId())
                .header("Authorization", "Bearer " + coordinatorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    /** Test 10 — disbanding a non-existent group returns 404 */
    @Test
    @Order(10)
    void disband_nonExistentGroup_returns404() throws Exception {
        mockMvc.perform(patch("/api/coordinator/groups/{id}/disband", UUID.randomUUID())
                .header("Authorization", "Bearer " + coordinatorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    // =========================================================================
    // RBAC — strict 403 for non-Coordinator roles
    // =========================================================================

    /** Test 11 — Professor token on members endpoint → 403 */
    @Test
    @Order(11)
    void memberManagement_professorToken_returns403() throws Exception {
        mockMvc.perform(patch("/api/coordinator/groups/{id}/members", group.getId())
                .header("Authorization", "Bearer " + professorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(memberBody("ADD", outsider.getStudentId())))
                .andExpect(status().isForbidden());
    }

    /** Test 12 — Admin token on members endpoint → 403 */
    @Test
    @Order(12)
    void memberManagement_adminToken_returns403() throws Exception {
        mockMvc.perform(patch("/api/coordinator/groups/{id}/members", group.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(memberBody("ADD", outsider.getStudentId())))
                .andExpect(status().isForbidden());
    }

    /** Test 13 — No token on members endpoint → 4xx */
    @Test
    @Order(13)
    void memberManagement_noToken_isRejected() throws Exception {
        mockMvc.perform(patch("/api/coordinator/groups/{id}/members", group.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(memberBody("ADD", outsider.getStudentId())))
                .andExpect(status().is4xxClientError());
    }

    /** Test 14 — Professor token on disband endpoint → 403 */
    @Test
    @Order(14)
    void disband_professorToken_returns403() throws Exception {
        mockMvc.perform(patch("/api/coordinator/groups/{id}/disband", group.getId())
                .header("Authorization", "Bearer " + professorToken))
                .andExpect(status().isForbidden());
    }

    /** Test 15 — Admin token on disband endpoint → 403 */
    @Test
    @Order(15)
    void disband_adminToken_returns403() throws Exception {
        mockMvc.perform(patch("/api/coordinator/groups/{id}/disband", group.getId())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private SystemConfig config(String key, String value) {
        SystemConfig c = new SystemConfig();
        c.setConfigKey(key);
        c.setConfigValue(value);
        return c;
    }

    private StaffUser newStaff(String mail, Role role) {
        StaffUser u = new StaffUser();
        u.setMail(mail);
        u.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
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

    private String memberBody(String action, String studentId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("action", action, "studentId", studentId));
    }
}
