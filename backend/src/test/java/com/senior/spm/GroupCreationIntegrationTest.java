package com.senior.spm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.GroupMembership.MemberRole;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.ScheduleWindow;
import com.senior.spm.entity.ScheduleWindow.WindowType;
import com.senior.spm.entity.Student;
import com.senior.spm.entity.SystemConfig;
import com.senior.spm.repository.AdvisorRequestRepository;
import com.senior.spm.repository.GroupInvitationRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.ScheduleWindowRepository;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for POST /api/groups (group creation).
 *
 * Covers: happy path DB integrity, all closed-window variants (no window, not yet open,
 * expired), duplicate group name, student already in a group, and unauthenticated access.
 *
 * References: Process 2 – DFD 2.1 (Group Creation & Schedule Window Enforcement).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(locations = "classpath:test.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GroupCreationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JWTService jwtService;

    @Autowired StudentRepository studentRepository;
    @Autowired SystemConfigRepository systemConfigRepository;
    @Autowired ScheduleWindowRepository scheduleWindowRepository;
    @Autowired ProjectGroupRepository projectGroupRepository;
    @Autowired GroupMembershipRepository groupMembershipRepository;
    @Autowired GroupInvitationRepository groupInvitationRepository;
    @Autowired AdvisorRequestRepository advisorRequestRepository;

    private static final String TERM_ID = "2026-SPRING-TEST";

    private Student testStudent;
    private String studentToken;

    @BeforeEach
    void setUp() {
        // Delete in FK-safe order so constraints don't block teardown between tests
        advisorRequestRepository.deleteAll();
        groupInvitationRepository.deleteAll();
        groupMembershipRepository.deleteAll();
        projectGroupRepository.deleteAll();
        studentRepository.deleteAll();
        scheduleWindowRepository.deleteAll();
        systemConfigRepository.deleteAll();

        // Seed the minimum system config that GroupService requires
        SystemConfig termConfig = new SystemConfig();
        termConfig.setConfigKey("active_term_id");
        termConfig.setConfigValue(TERM_ID);
        systemConfigRepository.save(termConfig);

        // Persist a student and issue a JWT for them
        testStudent = new Student();
        testStudent.setStudentId("12345678901");
        testStudent.setGithubUsername("test-gh-user");
        testStudent = studentRepository.save(testStudent);

        studentToken = jwtService.issueToken(testStudent);
    }

    // ─── Happy Path ───────────────────────────────────────────────────────────

    /**
     * AC: A student inside an active window gets 201 and both ProjectGroup and
     * GroupMembership rows land in the DB with correct values.
     */
    @Test
    @Order(1)
    void createGroup_withinActiveWindow_returns201AndPersistsRecords() throws Exception {
        openWindow();

        mockMvc.perform(post("/api/groups")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Alpha Team")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupName").value("Alpha Team"))
                .andExpect(jsonPath("$.status").value("FORMING"))
                .andExpect(jsonPath("$.termId").value(TERM_ID))
                .andExpect(jsonPath("$.members[0].role").value("TEAM_LEADER"));

        // Verify ProjectGroup record
        List<ProjectGroup> groups = projectGroupRepository.findAll();
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getGroupName()).isEqualTo("Alpha Team");
        assertThat(groups.get(0).getStatus()).isEqualTo(GroupStatus.FORMING);
        assertThat(groups.get(0).getTermId()).isEqualTo(TERM_ID);

        // Verify GroupMembership record — creator must be TEAM_LEADER
        List<GroupMembership> memberships = groupMembershipRepository.findAll();
        assertThat(memberships).hasSize(1);
        assertThat(memberships.get(0).getRole()).isEqualTo(MemberRole.TEAM_LEADER);
        assertThat(memberships.get(0).getStudent().getId()).isEqualTo(testStudent.getId());
        assertThat(memberships.get(0).getGroup().getId()).isEqualTo(groups.get(0).getId());
    }

    // ─── Closed-Window Scenarios (AC: must all produce 400) ──────────────────

    /**
     * No ScheduleWindow row exists for the current term → 400.
     */
    @Test
    @Order(2)
    void createGroup_noWindowExists_returns400() throws Exception {
        mockMvc.perform(post("/api/groups")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Beta Team")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        assertThat(projectGroupRepository.count()).isZero();
    }

    /**
     * Window exists but opensAt is in the future → 400.
     */
    @Test
    @Order(3)
    void createGroup_windowNotYetOpen_returns400() throws Exception {
        saveWindow(
                LocalDateTime.now(ZoneId.of("UTC")).plusDays(1),
                LocalDateTime.now(ZoneId.of("UTC")).plusDays(2));

        mockMvc.perform(post("/api/groups")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Gamma Team")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        assertThat(projectGroupRepository.count()).isZero();
    }

    /**
     * Window existed but closesAt is in the past → 400.
     */
    @Test
    @Order(4)
    void createGroup_windowExpired_returns400() throws Exception {
        saveWindow(
                LocalDateTime.now(ZoneId.of("UTC")).minusDays(2),
                LocalDateTime.now(ZoneId.of("UTC")).minusDays(1));

        mockMvc.perform(post("/api/groups")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Delta Team")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        assertThat(projectGroupRepository.count()).isZero();
    }

    // ─── Business-Rule Violations ─────────────────────────────────────────────

    /**
     * A group with the same name already exists in the same term → 409 Conflict.
     * No second group row must appear in the DB.
     */
    @Test
    @Order(5)
    void createGroup_duplicateName_returns409() throws Exception {
        openWindow();

        // Pre-seed a group occupying the name
        ProjectGroup existing = new ProjectGroup();
        existing.setGroupName("Taken Name");
        existing.setTermId(TERM_ID);
        existing.setStatus(GroupStatus.FORMING);
        existing.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));
        projectGroupRepository.save(existing);

        mockMvc.perform(post("/api/groups")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Taken Name")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());

        // Only the pre-seeded group must exist — no second row created
        assertThat(projectGroupRepository.count()).isEqualTo(1);
    }

    /**
     * Student is already a member of another group → 400.
     * Existing group and membership must be unchanged.
     */
    @Test
    @Order(6)
    void createGroup_studentAlreadyGrouped_returns400() throws Exception {
        openWindow();

        // Put testStudent in an existing group
        ProjectGroup existingGroup = new ProjectGroup();
        existingGroup.setGroupName("Already Here");
        existingGroup.setTermId(TERM_ID);
        existingGroup.setStatus(GroupStatus.FORMING);
        existingGroup.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));
        ProjectGroup saved = projectGroupRepository.save(existingGroup);

        GroupMembership membership = new GroupMembership();
        membership.setStudent(testStudent);
        membership.setGroup(saved);
        membership.setRole(MemberRole.TEAM_LEADER);
        membership.setJoinedAt(LocalDateTime.now(ZoneId.of("UTC")));
        groupMembershipRepository.save(membership);

        mockMvc.perform(post("/api/groups")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Second Group")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        assertThat(projectGroupRepository.count()).isEqualTo(1);
        assertThat(groupMembershipRepository.count()).isEqualTo(1);
    }

    // ─── Authentication ───────────────────────────────────────────────────────

    /**
     * No Authorization header → endpoint is protected, must be rejected (4xx).
     */
    @Test
    @Order(7)
    void createGroup_noToken_isRejected() throws Exception {
        openWindow();

        mockMvc.perform(post("/api/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Unauthorized Team")))
                .andExpect(status().is4xxClientError());

        assertThat(projectGroupRepository.count()).isZero();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void openWindow() {
        saveWindow(
                LocalDateTime.now(ZoneId.of("UTC")).minusHours(1),
                LocalDateTime.now(ZoneId.of("UTC")).plusHours(1));
    }

    private void saveWindow(LocalDateTime opensAt, LocalDateTime closesAt) {
        ScheduleWindow window = new ScheduleWindow();
        window.setTermId(TERM_ID);
        window.setType(WindowType.GROUP_CREATION);
        window.setOpensAt(opensAt);
        window.setClosesAt(closesAt);
        scheduleWindowRepository.save(window);
    }

    private String body(String groupName) throws Exception {
        return objectMapper.writeValueAsString(Map.of("groupName", groupName));
    }
}
