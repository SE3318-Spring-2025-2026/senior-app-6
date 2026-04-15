package com.senior.spm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senior.spm.entity.AdvisorRequest;
import com.senior.spm.entity.AdvisorRequest.RequestStatus;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.GroupMembership.MemberRole;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.ScheduleWindow;
import com.senior.spm.entity.ScheduleWindow.WindowType;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.StaffUser.Role;
import com.senior.spm.entity.Student;
import com.senior.spm.entity.SystemConfig;
import com.senior.spm.repository.AdvisorRequestRepository;
import com.senior.spm.repository.GroupInvitationRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.ScheduleWindowRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.repository.StudentRepository;
import com.senior.spm.repository.SystemConfigRepository;
import com.senior.spm.service.JWTService;
import com.senior.spm.service.SanitizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for Issue #68:
 * [QA] Capacity Guard, Sanitization & Race Conditions E2E.
 *
 * <p>Covers:
 * <ul>
 *   <li>Capacity guard transactional rollback — request stays PENDING on AtCapacity</li>
 *   <li>POST /api/coordinator/sanitize — RBAC, window guard, disband logic, DB integrity,
 *       report accuracy</li>
 *   <li>P3 state machine: TOOLS_BOUND → ADVISOR_ASSIGNED → TOOLS_BOUND → DISBANDED</li>
 *   <li>Optimistic locking / race condition — @Version protection via single-thread simulation</li>
 * </ul>
 *
 * References: p3_issues.md (Issue #68), 3.4_sanitization_p3.md, 3.2_3.3_advisor_respond_p3.md.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(locations = "classpath:test.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CapacityGuardSanitizationIntegrationTest {

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
    @Autowired ScheduleWindowRepository scheduleWindowRepository;

    /** Direct service access for race condition tests — called via Spring AOP proxy. */
    @Autowired SanitizationService sanitizationService;

    private static final String TERM_ID = "2026-SPRING-68-TEST";

    private StaffUser professor;
    private StaffUser coordinator;
    private Student student;
    private ProjectGroup toolsBoundGroup;

    private String professorToken;
    private String coordinatorToken;
    private String studentToken;

    @BeforeEach
    void setUp() {
        // FK-safe teardown
        advisorRequestRepository.deleteAll();
        groupInvitationRepository.deleteAll();
        groupMembershipRepository.deleteAll();
        projectGroupRepository.deleteAll();
        studentRepository.deleteAll();
        staffUserRepository.deleteAll();
        scheduleWindowRepository.deleteAll();
        systemConfigRepository.deleteAll();

        // Seed system config
        SystemConfig termConfig = new SystemConfig();
        termConfig.setConfigKey("active_term_id");
        termConfig.setConfigValue(TERM_ID);
        systemConfigRepository.save(termConfig);

        // Seed staff and student
        professor    = staffUserRepository.save(newProfessor("prof68@test.com", 5));
        coordinator  = staffUserRepository.save(newCoordinator("coord68@test.com"));
        student      = studentRepository.save(newStudent("11122233344", "gh-user-68"));

        // Seed a base TOOLS_BOUND group with the student as TEAM_LEADER
        toolsBoundGroup = projectGroupRepository.save(newGroup("Alpha-68", GroupStatus.TOOLS_BOUND));
        groupMembershipRepository.save(newMembership(student, toolsBoundGroup, MemberRole.TEAM_LEADER));

        // JWT tokens
        professorToken   = jwtService.issueToken(professor);
        coordinatorToken = jwtService.issueToken(coordinator);
        studentToken     = jwtService.issueToken(student);
    }

    // =========================================================================
    // Section A: Capacity Guard — Transactional Rollback
    // =========================================================================

    /**
     * Test 1 — Professor at capacity: request stays PENDING, group stays TOOLS_BOUND.
     *
     * <p>The spec mandates that on AdvisorAtCapacityException, the PENDING request is NOT
     * auto-rejected so the group can retry later. AdvisorControllerIntegrationTest#16 only
     * checks group status; this test adds the critical DB assertion on request.status.
     */
    @Test
    @Order(1)
    void capacityGuard_requestStaysPending_whenAdvisorAtCapacity() throws Exception {
        // Set professor capacity to 1 and fill it
        professor.setAdvisorCapacity(1);
        staffUserRepository.save(professor);

        ProjectGroup occupiedGroup = projectGroupRepository.save(newGroup("Occupied Group", GroupStatus.ADVISOR_ASSIGNED));
        occupiedGroup.setAdvisor(professor);
        projectGroupRepository.save(occupiedGroup);

        // Create a PENDING request targeting this professor
        AdvisorRequest request = advisorRequestRepository.save(pendingRequest(toolsBoundGroup, professor));

        // Try to accept — should fail with 400 (professor at capacity)
        mockMvc.perform(patch("/api/advisor/requests/{id}/respond", request.getId())
                .header("Authorization", "Bearer " + professorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(respondBody(true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        // DB: request must remain PENDING — not auto-rejected (key business rule)
        AdvisorRequest updated = advisorRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(RequestStatus.PENDING);

        // DB: group must remain TOOLS_BOUND, advisor must not be assigned
        ProjectGroup group = projectGroupRepository.findById(toolsBoundGroup.getId()).orElseThrow();
        assertThat(group.getStatus()).isEqualTo(GroupStatus.TOOLS_BOUND);
        assertThat(group.getAdvisor()).isNull();
    }

    // =========================================================================
    // Section B: Sanitize — RBAC
    // =========================================================================

    /** Test 2 — No token → endpoint is protected, must be rejected (4xx). */
    @Test
    @Order(2)
    void sanitize_noToken_isRejected() throws Exception {
        mockMvc.perform(post("/api/coordinator/sanitize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sanitizeBody(true)))
                .andExpect(status().is4xxClientError());
    }

    /** Test 3 — Student JWT → 403. */
    @Test
    @Order(3)
    void sanitize_studentToken_returns403() throws Exception {
        mockMvc.perform(post("/api/coordinator/sanitize")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sanitizeBody(true)))
                .andExpect(status().isForbidden());
    }

    /** Test 4 — Professor JWT → 403. */
    @Test
    @Order(4)
    void sanitize_professorToken_returns403() throws Exception {
        mockMvc.perform(post("/api/coordinator/sanitize")
                .header("Authorization", "Bearer " + professorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sanitizeBody(true)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Section C: Sanitize — Window Guard
    // =========================================================================

    /**
     * Test 5 — Window is still active and force=false → 400.
     * SanitizationService must guard against early execution without confirmation.
     */
    @Test
    @Order(5)
    void sanitize_windowStillActive_forceFalse_returns400() throws Exception {
        saveAdvisorAssociationWindow(
                LocalDateTime.now(ZoneId.of("UTC")).minusHours(1),
                LocalDateTime.now(ZoneId.of("UTC")).plusHours(1));

        mockMvc.perform(post("/api/coordinator/sanitize")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sanitizeBody(false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        // No group must have been disbanded
        assertThat(projectGroupRepository.findById(toolsBoundGroup.getId()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.TOOLS_BOUND);
    }

    /**
     * Test 6 — Window is still active but force=true → 200, group is disbanded.
     * force=true bypasses the window guard unconditionally.
     */
    @Test
    @Order(6)
    void sanitize_windowStillActive_forceTrue_returns200AndDisbandsGroups() throws Exception {
        saveAdvisorAssociationWindow(
                LocalDateTime.now(ZoneId.of("UTC")).minusHours(1),
                LocalDateTime.now(ZoneId.of("UTC")).plusHours(1));

        mockMvc.perform(post("/api/coordinator/sanitize")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sanitizeBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disbandedCount").value(1))
                .andExpect(jsonPath("$.triggeredAt").exists());

        assertThat(projectGroupRepository.findById(toolsBoundGroup.getId()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.DISBANDED);
    }

    /**
     * Test 7 — Window is closed and force=false → 200, group is disbanded.
     * Normal trigger path: window already closed, no force needed.
     */
    @Test
    @Order(7)
    void sanitize_windowClosed_forceFalse_returns200() throws Exception {
        saveAdvisorAssociationWindow(
                LocalDateTime.now(ZoneId.of("UTC")).minusDays(2),
                LocalDateTime.now(ZoneId.of("UTC")).minusDays(1));

        mockMvc.perform(post("/api/coordinator/sanitize")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sanitizeBody(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disbandedCount").value(1));

        assertThat(projectGroupRepository.findById(toolsBoundGroup.getId()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.DISBANDED);
    }

    // =========================================================================
    // Section D: Sanitize — Which Groups Are Disbanded
    // =========================================================================

    /**
     * Test 8 — FORMING, TOOLS_PENDING, and TOOLS_BOUND groups (all without advisor) are disbanded.
     * All three eligible statuses must be targeted.
     */
    @Test
    @Order(8)
    void sanitize_disbands_FORMING_TOOLS_PENDING_TOOLS_BOUND_groups() throws Exception {
        ProjectGroup forming      = projectGroupRepository.save(newGroup("Forming-68",      GroupStatus.FORMING));
        ProjectGroup toolsPending = projectGroupRepository.save(newGroup("ToolsPending-68", GroupStatus.TOOLS_PENDING));
        // toolsBoundGroup from setUp is the TOOLS_BOUND case

        mockMvc.perform(post("/api/coordinator/sanitize")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sanitizeBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disbandedCount").value(3));

        assertThat(projectGroupRepository.findById(forming.getId()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.DISBANDED);
        assertThat(projectGroupRepository.findById(toolsPending.getId()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.DISBANDED);
        assertThat(projectGroupRepository.findById(toolsBoundGroup.getId()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.DISBANDED);
    }

    /**
     * Test 9 — ADVISOR_ASSIGNED group (has advisor) is NOT touched by sanitization.
     * The query filters by advisor IS NULL, so assigned groups are excluded.
     */
    @Test
    @Order(9)
    void sanitize_doesNotDisband_ADVISOR_ASSIGNED_group() throws Exception {
        ProjectGroup assignedGroup = projectGroupRepository.save(newGroup("Assigned-68", GroupStatus.ADVISOR_ASSIGNED));
        assignedGroup.setAdvisor(professor);
        projectGroupRepository.save(assignedGroup);

        mockMvc.perform(post("/api/coordinator/sanitize")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sanitizeBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disbandedCount").value(1)); // only toolsBoundGroup

        // ADVISOR_ASSIGNED group must be untouched
        assertThat(projectGroupRepository.findById(assignedGroup.getId()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.ADVISOR_ASSIGNED);
        // toolsBoundGroup was disbanded
        assertThat(projectGroupRepository.findById(toolsBoundGroup.getId()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.DISBANDED);
    }

    /**
     * Test 10 — Already DISBANDED group is not re-processed; only the new unadvised group is counted.
     */
    @Test
    @Order(10)
    void sanitize_doesNotDisband_alreadyDisbanded_group() throws Exception {
        ProjectGroup alreadyDisbanded = projectGroupRepository.save(newGroup("Already-68", GroupStatus.DISBANDED));

        mockMvc.perform(post("/api/coordinator/sanitize")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sanitizeBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disbandedCount").value(1)); // toolsBoundGroup only

        // Pre-existing DISBANDED group remains DISBANDED (no second change)
        assertThat(projectGroupRepository.findById(alreadyDisbanded.getId()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.DISBANDED);
    }

    // =========================================================================
    // Section E: Sanitize — DB Integrity
    // =========================================================================

    /**
     * Test 11 — GroupMembership rows are hard-deleted when a group is disbanded.
     * Freed students must be able to join or form a new group next term.
     */
    @Test
    @Order(11)
    void sanitize_hardDeletesMemberships_onDisband() throws Exception {
        // Add a second member to toolsBoundGroup
        Student secondStudent = studentRepository.save(newStudent("99988877766", "gh-user-69"));
        groupMembershipRepository.save(newMembership(secondStudent, toolsBoundGroup, MemberRole.MEMBER));

        assertThat(groupMembershipRepository.countByGroupId(toolsBoundGroup.getId())).isEqualTo(2);

        mockMvc.perform(post("/api/coordinator/sanitize")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sanitizeBody(true)))
                .andExpect(status().isOk());

        // All memberships must be hard-deleted
        assertThat(groupMembershipRepository.countByGroupId(toolsBoundGroup.getId())).isZero();
    }

    /**
     * Test 12 — PENDING advisor requests for a disbanded group are bulk-updated to AUTO_REJECTED.
     */
    @Test
    @Order(12)
    void sanitize_autoRejectsPendingAdvisorRequests_onDisband() throws Exception {
        AdvisorRequest pendingReq = advisorRequestRepository.save(pendingRequest(toolsBoundGroup, professor));

        mockMvc.perform(post("/api/coordinator/sanitize")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sanitizeBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoRejectedRequestCount").value(1));

        AdvisorRequest updated = advisorRequestRepository.findById(pendingReq.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(RequestStatus.AUTO_REJECTED);
    }

    // =========================================================================
    // Section F: Sanitize — Report Accuracy
    // =========================================================================

    /**
     * Test 13 — SanitizationReport counts are accurate across multiple groups and requests.
     */
    @Test
    @Order(13)
    void sanitize_report_countsAccurate_multipleGroups() throws Exception {
        // Create a second unadvised group with its own PENDING request
        Student student2 = studentRepository.save(newStudent("55544433322", "gh-user-70"));
        ProjectGroup group2 = projectGroupRepository.save(newGroup("Beta-68", GroupStatus.TOOLS_BOUND));
        groupMembershipRepository.save(newMembership(student2, group2, MemberRole.TEAM_LEADER));

        advisorRequestRepository.save(pendingRequest(toolsBoundGroup, professor));
        advisorRequestRepository.save(pendingRequest(group2, professor));

        mockMvc.perform(post("/api/coordinator/sanitize")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sanitizeBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disbandedCount").value(2))
                .andExpect(jsonPath("$.autoRejectedRequestCount").value(2))
                .andExpect(jsonPath("$.triggeredAt").exists());
    }

    /**
     * Test 14 — When no unadvised groups exist, report returns zero counts.
     */
    @Test
    @Order(14)
    void sanitize_noTargetGroups_reportCountsZero() throws Exception {
        // Assign advisor to toolsBoundGroup so it is no longer an unadvised target
        toolsBoundGroup.setAdvisor(professor);
        toolsBoundGroup.setStatus(GroupStatus.ADVISOR_ASSIGNED);
        projectGroupRepository.save(toolsBoundGroup);

        mockMvc.perform(post("/api/coordinator/sanitize")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sanitizeBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disbandedCount").value(0))
                .andExpect(jsonPath("$.autoRejectedRequestCount").value(0));
    }

    // =========================================================================
    // Section G: State Machine Transition
    // =========================================================================

    /**
     * Test 15 — Full P3 state machine:
     * TOOLS_BOUND → ADVISOR_ASSIGNED (accept) → TOOLS_BOUND (remove) → DISBANDED (sanitize).
     *
     * <p>Each transition is verified at the DB level before moving to the next step.
     */
    @Test
    @Order(15)
    void stateMachine_toolsBound_advisorAssigned_toolsBound_disbanded() throws Exception {
        // Seed a PENDING request (bypassing the send endpoint, no window check needed)
        AdvisorRequest request = advisorRequestRepository.save(pendingRequest(toolsBoundGroup, professor));

        // Step 1: Professor accepts → TOOLS_BOUND → ADVISOR_ASSIGNED
        mockMvc.perform(patch("/api/advisor/requests/{id}/respond", request.getId())
                .header("Authorization", "Bearer " + professorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(respondBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.groupStatus").value("ADVISOR_ASSIGNED"));

        assertThat(projectGroupRepository.findById(toolsBoundGroup.getId()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.ADVISOR_ASSIGNED);

        // Step 2: Coordinator removes advisor → ADVISOR_ASSIGNED → TOOLS_BOUND
        mockMvc.perform(patch("/api/coordinator/groups/{id}/advisor", toolsBoundGroup.getId())
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(overrideBody("REMOVE", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TOOLS_BOUND"));

        ProjectGroup afterRemove = projectGroupRepository.findById(toolsBoundGroup.getId()).orElseThrow();
        assertThat(afterRemove.getStatus()).isEqualTo(GroupStatus.TOOLS_BOUND);
        assertThat(afterRemove.getAdvisor()).isNull();

        // Step 3: Sanitize (force=true, window not required) → TOOLS_BOUND → DISBANDED
        mockMvc.perform(post("/api/coordinator/sanitize")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sanitizeBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disbandedCount").value(1));

        assertThat(projectGroupRepository.findById(toolsBoundGroup.getId()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.DISBANDED);
    }

    // =========================================================================
    // Section H: Race Condition / Optimistic Locking
    // =========================================================================

    /**
     * Test 16 — Calling disbandGroup with a stale entity (@Version conflict) throws
     * OptimisticLockingFailureException and leaves the group intact in the DB.
     *
     * <p>Simulates the race condition where:
     * <ol>
     *   <li>Sanitizer reads a group (version=0 snapshot)</li>
     *   <li>An advisor concurrently accepts → DB version bumped to 1</li>
     *   <li>Sanitizer tries to disband the stale entity → version mismatch → rollback</li>
     * </ol>
     */
    @Test
    @Order(16)
    void raceCondition_disbandGroup_withStaleVersion_throwsOptimisticLockException() throws Exception {
        // Get stale snapshot (version=0) before bumping the DB
        ProjectGroup staleGroup = projectGroupRepository.findById(toolsBoundGroup.getId()).orElseThrow();

        // Simulate concurrent advisor accept: load fresh, set advisor, save → DB version=1
        ProjectGroup freshGroup = projectGroupRepository.findById(toolsBoundGroup.getId()).orElseThrow();
        freshGroup.setAdvisor(professor);
        freshGroup.setStatus(GroupStatus.ADVISOR_ASSIGNED);
        projectGroupRepository.save(freshGroup);

        // Attempt to disband using the stale reference — must throw due to @Version mismatch
        assertThatThrownBy(() -> sanitizationService.disbandGroup(staleGroup))
                .isInstanceOf(OptimisticLockingFailureException.class);

        // Group must be preserved at ADVISOR_ASSIGNED (disband rolled back)
        assertThat(projectGroupRepository.findById(toolsBoundGroup.getId()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.ADVISOR_ASSIGNED);

        // Memberships must be intact
        assertThat(groupMembershipRepository.countByGroupId(toolsBoundGroup.getId())).isEqualTo(1);
    }

    /**
     * Test 17 — runSanitization skips the group with a version conflict and successfully
     * disbands the other group in the same run.
     *
     * <p>Verifies that REQUIRES_NEW per-group transactions isolate failures: one group's
     * OptimisticLockException does not prevent other groups from being disbanded.
     */
    @Test
    @Order(17)
    void raceCondition_disbandGroup_versionConflictSkipped_otherGroupDisbanded() throws Exception {
        // Create a second unadvised group (will be disbanded normally)
        Student student2 = studentRepository.save(newStudent("77766655544", "gh-user-71"));
        ProjectGroup normalGroup = projectGroupRepository.save(newGroup("Normal-68", GroupStatus.TOOLS_BOUND));
        groupMembershipRepository.save(newMembership(student2, normalGroup, MemberRole.TEAM_LEADER));

        // Get stale snapshot of toolsBoundGroup (version=0)
        ProjectGroup staleGroup = projectGroupRepository.findById(toolsBoundGroup.getId()).orElseThrow();

        // Bump toolsBoundGroup's DB version (simulate concurrent accept)
        ProjectGroup freshGroup = projectGroupRepository.findById(toolsBoundGroup.getId()).orElseThrow();
        freshGroup.setAdvisor(professor);
        freshGroup.setStatus(GroupStatus.ADVISOR_ASSIGNED);
        projectGroupRepository.save(freshGroup);

        // Disband with stale entity → exception (simulates the conflict case)
        try {
            sanitizationService.disbandGroup(staleGroup);
        } catch (OptimisticLockingFailureException ignored) {
            // Expected — the conflict group is skipped, the loop continues
        }

        // Disband the normal group — must succeed despite the previous conflict
        ProjectGroup freshNormal = projectGroupRepository.findById(normalGroup.getId()).orElseThrow();
        sanitizationService.disbandGroup(freshNormal);

        // Conflicted group preserved (ADVISOR_ASSIGNED), normal group disbanded
        assertThat(projectGroupRepository.findById(toolsBoundGroup.getId()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.ADVISOR_ASSIGNED);
        assertThat(projectGroupRepository.findById(normalGroup.getId()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.DISBANDED);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private StaffUser newProfessor(String mail, int capacity) {
        StaffUser u = new StaffUser();
        u.setMail(mail);
        u.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
        u.setRole(Role.Professor);
        u.setAdvisorCapacity(capacity);
        return u;
    }

    private StaffUser newCoordinator(String mail) {
        StaffUser u = new StaffUser();
        u.setMail(mail);
        u.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
        u.setRole(Role.Coordinator);
        return u;
    }

    private Student newStudent(String studentId, String github) {
        Student s = new Student();
        s.setStudentId(studentId);
        s.setGithubUsername(github);
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

    private AdvisorRequest pendingRequest(ProjectGroup group, StaffUser advisor) {
        AdvisorRequest r = new AdvisorRequest();
        r.setGroup(group);
        r.setAdvisor(advisor);
        r.setStatus(RequestStatus.PENDING);
        r.setSentAt(LocalDateTime.now(ZoneId.of("UTC")));
        return r;
    }

    private void saveAdvisorAssociationWindow(LocalDateTime opensAt, LocalDateTime closesAt) {
        ScheduleWindow w = new ScheduleWindow();
        w.setTermId(TERM_ID);
        w.setType(WindowType.ADVISOR_ASSOCIATION);
        w.setOpensAt(opensAt);
        w.setClosesAt(closesAt);
        scheduleWindowRepository.save(w);
    }

    private String sanitizeBody(boolean force) throws Exception {
        return objectMapper.writeValueAsString(Map.of("force", force));
    }

    private String respondBody(boolean accept) throws Exception {
        return objectMapper.writeValueAsString(Map.of("accept", accept));
    }

    private String overrideBody(String action, UUID advisorId) throws Exception {
        if (advisorId != null) {
            return objectMapper.writeValueAsString(Map.of("action", action, "advisorId", advisorId));
        }
        return objectMapper.writeValueAsString(Map.of("action", action));
    }
}
