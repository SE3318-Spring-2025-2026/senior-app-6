package com.senior.spm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senior.spm.entity.AdvisorRequest;
import com.senior.spm.entity.AdvisorRequest.RequestStatus;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.GroupMembership.MemberRole;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.StaffUser.Role;
import com.senior.spm.entity.Student;
import com.senior.spm.entity.SystemConfig;
import com.senior.spm.repository.AdvisorRequestRepository;
import com.senior.spm.repository.CommitteeRepository;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Black-box integration tests for AdvisorController (P3-API-02 & P3-API-03).
 *
 * All 20 tests pass green against the full Spring Boot context and H2 in-memory DB.
 *
 * Endpoints covered:
 *   GET  /api/advisor/requests
 *   GET  /api/advisor/requests/{requestId}
 *   PATCH /api/advisor/requests/{requestId}/respond
 *
 * References: docs/process3/sequences/3.2_3.3_advisor_respond_p3.md
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(locations = "classpath:test.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdvisorControllerIntegrationTest {

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
    @Autowired CommitteeRepository committeeRepository;

    private static final String TERM_ID = "2026-SPRING-TEST";

    // Shared test fixtures — re-created fresh in @BeforeEach
    private StaffUser professor;
    private StaffUser coordinator;
    private Student student;
    private ProjectGroup toolsBoundGroup;

    private String professorToken;
    private String coordinatorToken;
    private String studentToken;

    @BeforeEach
    void setUp() {
        // FK-safe teardown.
        // committeeRepository.deleteAll() removes committee_group join rows first (ManyToMany owning side)
        // so that projectGroupRepository.deleteAll() is not blocked by the FK constraint.
        advisorRequestRepository.deleteAll();
        groupInvitationRepository.deleteAll();
        groupMembershipRepository.deleteAll();
        committeeRepository.deleteAll();
        projectGroupRepository.deleteAll();
        studentRepository.deleteAll();
        staffUserRepository.deleteAll();
        systemConfigRepository.deleteAll();

        // System config
        SystemConfig termConfig = new SystemConfig();
        termConfig.setConfigKey("active_term_id");
        termConfig.setConfigValue(TERM_ID);
        systemConfigRepository.save(termConfig);

        // Professor (default capacity = 5)
        professor = staffUserRepository.save(newProfessor("prof@test.com", 5));
        coordinator = staffUserRepository.save(newCoordinator("coord@test.com"));

        // Student + group in TOOLS_BOUND state
        student = studentRepository.save(newStudent("12345678901", "gh-user"));
        toolsBoundGroup = projectGroupRepository.save(newGroup("Alpha Team", GroupStatus.TOOLS_BOUND));
        groupMembershipRepository.save(newMembership(student, toolsBoundGroup, MemberRole.TEAM_LEADER));

        // JWT tokens
        professorToken  = jwtService.issueToken(professor);
        coordinatorToken = jwtService.issueToken(coordinator);
        studentToken    = jwtService.issueToken(student);
    }

    // =========================================================================
    // GET /api/advisor/requests
    // =========================================================================

    /** Test 1 — returns the professor's PENDING requests */
    @Test
    @Order(1)
    void listRequests_withPendingRequests_returns200WithList() throws Exception {
        advisorRequestRepository.save(pendingRequest(toolsBoundGroup, professor));

        mockMvc.perform(get("/api/advisor/requests")
                .header("Authorization", "Bearer " + professorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").exists())
                .andExpect(jsonPath("$[0].groupId").exists())
                .andExpect(jsonPath("$[0].groupName").value("Alpha Team"))
                .andExpect(jsonPath("$[0].termId").value(TERM_ID))
                .andExpect(jsonPath("$[0].memberCount").value(1))
                .andExpect(jsonPath("$[0].sentAt").exists());
    }

    /** Test 2 — empty list (not 404) when no pending requests */
    @Test
    @Order(2)
    void listRequests_noPendingRequests_returns200WithEmptyArray() throws Exception {
        mockMvc.perform(get("/api/advisor/requests")
                .header("Authorization", "Bearer " + professorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    /** Test 3 — no token → rejected */
    @Test
    @Order(3)
    void listRequests_noToken_isRejected() throws Exception {
        mockMvc.perform(get("/api/advisor/requests"))
                .andExpect(status().is4xxClientError());
    }

    /** Test 4 — student token → 403 */
    @Test
    @Order(4)
    void listRequests_studentToken_returns403() throws Exception {
        mockMvc.perform(get("/api/advisor/requests")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    /** Test 5 — coordinator token → 403 */
    @Test
    @Order(5)
    void listRequests_coordinatorToken_returns403() throws Exception {
        mockMvc.perform(get("/api/advisor/requests")
                .header("Authorization", "Bearer " + coordinatorToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // GET /api/advisor/requests/{requestId}
    // =========================================================================

    /** Test 6 — professor fetches their own request detail */
    @Test
    @Order(6)
    void getRequestDetail_ownRequest_returns200WithDetail() throws Exception {
        AdvisorRequest req = advisorRequestRepository.save(pendingRequest(toolsBoundGroup, professor));

        mockMvc.perform(get("/api/advisor/requests/{id}", req.getId())
                .header("Authorization", "Bearer " + professorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(req.getId().toString()))
                .andExpect(jsonPath("$.group.id").value(toolsBoundGroup.getId().toString()))
                .andExpect(jsonPath("$.group.groupName").value("Alpha Team"))
                .andExpect(jsonPath("$.group.termId").value(TERM_ID))
                .andExpect(jsonPath("$.group.status").value("TOOLS_BOUND"))
                .andExpect(jsonPath("$.group.members").isArray())
                .andExpect(jsonPath("$.sentAt").exists());
    }

    /** Test 7 — professor fetches another professor's request → 403 */
    @Test
    @Order(7)
    void getRequestDetail_otherProfessorsRequest_returns403() throws Exception {
        StaffUser otherProf = staffUserRepository.save(newProfessor("other@test.com", 5));
        AdvisorRequest req = advisorRequestRepository.save(pendingRequest(toolsBoundGroup, otherProf));

        mockMvc.perform(get("/api/advisor/requests/{id}", req.getId())
                .header("Authorization", "Bearer " + professorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());
    }

    /** Test 8 — request does not exist → 404 */
    @Test
    @Order(8)
    void getRequestDetail_nonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/advisor/requests/{id}", UUID.randomUUID())
                .header("Authorization", "Bearer " + professorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    /** Test 9 — no token → rejected */
    @Test
    @Order(9)
    void getRequestDetail_noToken_isRejected() throws Exception {
        mockMvc.perform(get("/api/advisor/requests/{id}", UUID.randomUUID()))
                .andExpect(status().is4xxClientError());
    }

    // =========================================================================
    // PATCH /api/advisor/requests/{requestId}/respond
    // =========================================================================

    /** Test 10 — accept: group becomes ADVISOR_ASSIGNED, request ACCEPTED */
    @Test
    @Order(10)
    void respond_accept_returns200AndUpdatesDb() throws Exception {
        AdvisorRequest req = advisorRequestRepository.save(pendingRequest(toolsBoundGroup, professor));

        mockMvc.perform(patch("/api/advisor/requests/{id}/respond", req.getId())
                .header("Authorization", "Bearer " + professorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(respondBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(req.getId().toString()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.groupId").value(toolsBoundGroup.getId().toString()))
                .andExpect(jsonPath("$.groupStatus").value("ADVISOR_ASSIGNED"));

        // DB: group status and advisor must be updated
        ProjectGroup updated = projectGroupRepository.findById(toolsBoundGroup.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(GroupStatus.ADVISOR_ASSIGNED);
        assertThat(updated.getAdvisor().getId()).isEqualTo(professor.getId());
    }

    /** Test 11 — reject: request REJECTED, group stays TOOLS_BOUND */
    @Test
    @Order(11)
    void respond_reject_returns200AndGroupStatusUnchanged() throws Exception {
        AdvisorRequest req = advisorRequestRepository.save(pendingRequest(toolsBoundGroup, professor));

        mockMvc.perform(patch("/api/advisor/requests/{id}/respond", req.getId())
                .header("Authorization", "Bearer " + professorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(respondBody(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(req.getId().toString()))
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // DB: group must remain TOOLS_BOUND, no advisor set
        ProjectGroup unchanged = projectGroupRepository.findById(toolsBoundGroup.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(GroupStatus.TOOLS_BOUND);
        assertThat(unchanged.getAdvisor()).isNull();
    }

    /** Test 12 — non-existent request → 404 */
    @Test
    @Order(12)
    void respond_nonExistentRequest_returns404() throws Exception {
        mockMvc.perform(patch("/api/advisor/requests/{id}/respond", UUID.randomUUID())
                .header("Authorization", "Bearer " + professorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(respondBody(true)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    /** Test 13 — professor tries to respond to another professor's request → 403 */
    @Test
    @Order(13)
    void respond_otherProfessorsRequest_returns403() throws Exception {
        StaffUser otherProf = staffUserRepository.save(newProfessor("other2@test.com", 5));
        AdvisorRequest req = advisorRequestRepository.save(pendingRequest(toolsBoundGroup, otherProf));

        mockMvc.perform(patch("/api/advisor/requests/{id}/respond", req.getId())
                .header("Authorization", "Bearer " + professorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(respondBody(true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());
    }

    /** Test 14 — request already REJECTED → 400 */
    @Test
    @Order(14)
    void respond_alreadyRejectedRequest_returns400() throws Exception {
        AdvisorRequest req = pendingRequest(toolsBoundGroup, professor);
        req.setStatus(RequestStatus.REJECTED);
        req.setRespondedAt(LocalDateTime.now(ZoneId.of("UTC")));
        advisorRequestRepository.save(req);

        mockMvc.perform(patch("/api/advisor/requests/{id}/respond", req.getId())
                .header("Authorization", "Bearer " + professorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(respondBody(true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    /** Test 15 — request already ACCEPTED → 400 */
    @Test
    @Order(15)
    void respond_alreadyAcceptedRequest_returns400() throws Exception {
        AdvisorRequest req = pendingRequest(toolsBoundGroup, professor);
        req.setStatus(RequestStatus.ACCEPTED);
        req.setRespondedAt(LocalDateTime.now(ZoneId.of("UTC")));
        advisorRequestRepository.save(req);

        mockMvc.perform(patch("/api/advisor/requests/{id}/respond", req.getId())
                .header("Authorization", "Bearer " + professorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(respondBody(true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    /** Test 16 — professor is at capacity → 400 */
    @Test
    @Order(16)
    void respond_accept_professorAtCapacity_returns400() throws Exception {
        // Set professor capacity to 1
        professor.setAdvisorCapacity(1);
        staffUserRepository.save(professor);

        // Assign one group already (fills the capacity)
        ProjectGroup occupiedGroup = projectGroupRepository.save(newGroup("Occupied Group", GroupStatus.ADVISOR_ASSIGNED));
        occupiedGroup.setAdvisor(professor);
        projectGroupRepository.save(occupiedGroup);

        // New pending request on a different group
        AdvisorRequest req = advisorRequestRepository.save(pendingRequest(toolsBoundGroup, professor));

        mockMvc.perform(patch("/api/advisor/requests/{id}/respond", req.getId())
                .header("Authorization", "Bearer " + professorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(respondBody(true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        // Group must remain TOOLS_BOUND
        assertThat(projectGroupRepository.findById(toolsBoundGroup.getId()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.TOOLS_BOUND);
    }

    /**
     * Test 17 — accept: other PENDING requests for the same group become AUTO_REJECTED.
     * Simulates two advisors receiving requests for the same group; first one to accept
     * must auto-reject the other.
     */
    @Test
    @Order(17)
    void respond_accept_autoRejectsOtherPendingRequestsForSameGroup() throws Exception {
        StaffUser secondProfessor = staffUserRepository.save(newProfessor("prof2@test.com", 5));

        AdvisorRequest req1 = advisorRequestRepository.save(pendingRequest(toolsBoundGroup, professor));
        AdvisorRequest req2 = advisorRequestRepository.save(pendingRequest(toolsBoundGroup, secondProfessor));

        // Professor 1 accepts
        mockMvc.perform(patch("/api/advisor/requests/{id}/respond", req1.getId())
                .header("Authorization", "Bearer " + professorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(respondBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // req2 must now be AUTO_REJECTED in the DB
        AdvisorRequest req2Updated = advisorRequestRepository.findById(req2.getId()).orElseThrow();
        assertThat(req2Updated.getStatus()).isEqualTo(RequestStatus.AUTO_REJECTED);

        // No PENDING requests remain for the group
        List<AdvisorRequest> remaining = advisorRequestRepository.findByGroupId(toolsBoundGroup.getId());
        assertThat(remaining).noneMatch(r -> r.getStatus() == RequestStatus.PENDING);
    }

    /** Test 18 — reject: group status and other requests are completely unaffected */
    @Test
    @Order(18)
    void respond_reject_doesNotAffectOtherRequestsOrGroupStatus() throws Exception {
        StaffUser secondProfessor = staffUserRepository.save(newProfessor("prof3@test.com", 5));

        AdvisorRequest req1 = advisorRequestRepository.save(pendingRequest(toolsBoundGroup, professor));
        AdvisorRequest req2 = advisorRequestRepository.save(pendingRequest(toolsBoundGroup, secondProfessor));

        // Professor 1 rejects
        mockMvc.perform(patch("/api/advisor/requests/{id}/respond", req1.getId())
                .header("Authorization", "Bearer " + professorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(respondBody(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // req2 must still be PENDING
        assertThat(advisorRequestRepository.findById(req2.getId()).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.PENDING);

        // Group status must remain TOOLS_BOUND
        assertThat(projectGroupRepository.findById(toolsBoundGroup.getId()).orElseThrow().getStatus())
                .isEqualTo(GroupStatus.TOOLS_BOUND);
    }

    /** Test 19 — no token → rejected */
    @Test
    @Order(19)
    void respond_noToken_isRejected() throws Exception {
        AdvisorRequest req = advisorRequestRepository.save(pendingRequest(toolsBoundGroup, professor));

        mockMvc.perform(patch("/api/advisor/requests/{id}/respond", req.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(respondBody(true)))
                .andExpect(status().is4xxClientError());
    }

    /** Test 20 — student token → 403 */
    @Test
    @Order(20)
    void respond_studentToken_returns403() throws Exception {
        AdvisorRequest req = advisorRequestRepository.save(pendingRequest(toolsBoundGroup, professor));

        mockMvc.perform(patch("/api/advisor/requests/{id}/respond", req.getId())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(respondBody(true)))
                .andExpect(status().isForbidden());
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

    private AdvisorRequest pendingRequest(ProjectGroup group, StaffUser advisor) {
        AdvisorRequest r = new AdvisorRequest();
        r.setGroup(group);
        r.setAdvisor(advisor);
        r.setStatus(RequestStatus.PENDING);
        r.setSentAt(LocalDateTime.now(ZoneId.of("UTC")));
        return r;
    }

    private String respondBody(boolean accept) throws Exception {
        return objectMapper.writeValueAsString(Map.of("accept", accept));
    }
}
