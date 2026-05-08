package com.senior.spm.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senior.spm.entity.Committee;
import com.senior.spm.entity.CommitteeProfessor;
import com.senior.spm.entity.CommitteeProfessor.ProfessorRole;
import com.senior.spm.entity.Deliverable;
import com.senior.spm.entity.Deliverable.DeliverableType;
import com.senior.spm.entity.GroupMembership;
import com.senior.spm.entity.GroupMembership.MemberRole;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.StaffUser.Role;
import com.senior.spm.entity.Student;
import com.senior.spm.repository.CommitteeProfessorRepository;
import com.senior.spm.repository.CommitteeRepository;
import com.senior.spm.repository.DeliverableRepository;
import com.senior.spm.repository.DeliverableSubmissionRepository;
import com.senior.spm.repository.GroupMembershipRepository;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.repository.StudentRepository;
import com.senior.spm.service.JWTService;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubmissionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Autowired
    private DeliverableSubmissionRepository deliverableSubmissionRepository;

    @Autowired
    private DeliverableRepository deliverableRepository;

    @Autowired
    private ProjectGroupRepository projectGroupRepository;

    @Autowired
    private GroupMembershipRepository groupMembershipRepository;

    @Autowired
    private CommitteeRepository committeeRepository;

    @Autowired
    private CommitteeProfessorRepository committeeProfessorRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID validDeliverableId;
    private UUID expiredDeliverableId;
    private UUID noCommitteeDeliverableId;

    @BeforeAll
    void setUpOnce() {
        // Tear down in FK-safe order
        deliverableSubmissionRepository.deleteAll();
        committeeProfessorRepository.deleteAll();
        committeeRepository.deleteAll();
        groupMembershipRepository.deleteAll();
        projectGroupRepository.deleteAll();
        deliverableRepository.deleteAll();
        studentRepository.deleteAll();
        staffUserRepository.deleteAll();

        // Staff users
        StaffUser admin = new StaffUser();
        admin.setMail("test@test.com");
        admin.setPasswordHash("$2a$10$tj811p0KDPOD6Dd58xb0.uBNIT8.CXeJPKoSUSwPuJ0BI.RuC5yGq");
        admin.setRole(Role.Admin);
        admin.setFirstLogin(false);
        admin.setAdvisorCapacity(0);
        staffUserRepository.save(admin);

        StaffUser professorA = new StaffUser();
        professorA.setMail("professor@test.com");
        professorA.setPasswordHash("$2a$10$tj811p0KDPOD6Dd58xb0.uBNIT8.CXeJPKoSUSwPuJ0BI.RuC5yGq");
        professorA.setRole(Role.Professor);
        professorA.setFirstLogin(false);
        professorA.setAdvisorCapacity(5);
        professorA = staffUserRepository.save(professorA);

        StaffUser professorB = new StaffUser();
        professorB.setMail("professorb@test.com");
        professorB.setPasswordHash("$2a$10$tj811p0KDPOD6Dd58xb0.uBNIT8.CXeJPKoSUSwPuJ0BI.RuC5yGq");
        professorB.setRole(Role.Professor);
        professorB.setFirstLogin(false);
        professorB.setAdvisorCapacity(5);
        staffUserRepository.save(professorB);

        // Students
        Student teamLeader = new Student();
        teamLeader.setStudentId("23070006018");
        teamLeader = studentRepository.save(teamLeader);

        Student member = new Student();
        member.setStudentId("23070006019");
        studentRepository.save(member);

        // Deliverables
        Deliverable validDeliverable = new Deliverable();
        validDeliverable.setName("QA Valid Proposal");
        validDeliverable.setType(DeliverableType.Proposal);
        validDeliverable.setSubmissionDeadline(LocalDateTime.of(2099, 12, 31, 23, 59, 59));
        validDeliverable.setReviewDeadline(LocalDateTime.of(2099, 12, 31, 23, 59, 59));
        validDeliverable.setWeight(new BigDecimal("20.00"));
        validDeliverable = deliverableRepository.save(validDeliverable);
        validDeliverableId = validDeliverable.getId();

        Deliverable expiredDeliverable = new Deliverable();
        expiredDeliverable.setName("QA Expired Proposal");
        expiredDeliverable.setType(DeliverableType.Proposal);
        expiredDeliverable.setSubmissionDeadline(LocalDateTime.of(2000, 1, 1, 0, 0, 0));
        expiredDeliverable.setReviewDeadline(LocalDateTime.of(2099, 12, 31, 23, 59, 59));
        expiredDeliverable.setWeight(new BigDecimal("20.00"));
        expiredDeliverable = deliverableRepository.save(expiredDeliverable);
        expiredDeliverableId = expiredDeliverable.getId();

        Deliverable noCommitteeDeliverable = new Deliverable();
        noCommitteeDeliverable.setName("QA No Committee Proposal");
        noCommitteeDeliverable.setType(DeliverableType.Proposal);
        noCommitteeDeliverable.setSubmissionDeadline(LocalDateTime.of(2099, 12, 31, 23, 59, 59));
        noCommitteeDeliverable.setReviewDeadline(LocalDateTime.of(2099, 12, 31, 23, 59, 59));
        noCommitteeDeliverable.setWeight(new BigDecimal("20.00"));
        noCommitteeDeliverable = deliverableRepository.save(noCommitteeDeliverable);
        noCommitteeDeliverableId = noCommitteeDeliverable.getId();

        // Project group
        ProjectGroup group = new ProjectGroup();
        group.setGroupName("QA Test Group");
        group.setStatus(GroupStatus.TOOLS_BOUND);
        group.setTermId("2026-SPRING");
        group.setCreatedAt(LocalDateTime.now());
        group = projectGroupRepository.save(group);

        // Group memberships
        GroupMembership leaderMembership = new GroupMembership();
        leaderMembership.setGroup(group);
        leaderMembership.setStudent(teamLeader);
        leaderMembership.setRole(MemberRole.TEAM_LEADER);
        leaderMembership.setJoinedAt(LocalDateTime.now());
        groupMembershipRepository.save(leaderMembership);

        GroupMembership memberMembership = new GroupMembership();
        memberMembership.setGroup(group);
        memberMembership.setStudent(member);
        memberMembership.setRole(MemberRole.MEMBER);
        memberMembership.setJoinedAt(LocalDateTime.now());
        groupMembershipRepository.save(memberMembership);

        // Committee for valid deliverable (group + professorA assigned)
        Committee committeeValid = new Committee();
        committeeValid.setCommitteeName("QA Committee A");
        committeeValid.setTermId("2026-SPRING");
        committeeValid.setDeliverable(validDeliverable);
        committeeValid.getGroups().add(group);
        committeeValid = committeeRepository.save(committeeValid);

        CommitteeProfessor cpValid = new CommitteeProfessor();
        cpValid.setCommittee(committeeValid);
        cpValid.setProfessor(professorA);
        cpValid.setRole(ProfessorRole.ADVISOR);
        committeeProfessorRepository.save(cpValid);

        // Committee for expired deliverable (group + professorA assigned)
        Committee committeeExpired = new Committee();
        committeeExpired.setCommitteeName("QA Committee Expired");
        committeeExpired.setTermId("2026-SPRING");
        committeeExpired.setDeliverable(expiredDeliverable);
        committeeExpired.getGroups().add(group);
        committeeExpired = committeeRepository.save(committeeExpired);

        CommitteeProfessor cpExpired = new CommitteeProfessor();
        cpExpired.setCommittee(committeeExpired);
        cpExpired.setProfessor(professorA);
        cpExpired.setRole(ProfessorRole.ADVISOR);
        committeeProfessorRepository.save(cpExpired);
    }

    @AfterAll
    void tearDownOnce() {
        deliverableSubmissionRepository.deleteAll();
        committeeProfessorRepository.deleteAll();
        committeeRepository.deleteAll();
        groupMembershipRepository.deleteAll();
        projectGroupRepository.deleteAll();
        deliverableRepository.deleteAll();
        studentRepository.deleteAll();
        staffUserRepository.deleteAll();
    }

    @BeforeEach
    void cleanSubmissions() {
        deliverableSubmissionRepository.deleteAll();
    }

    private String teamLeaderToken() {
        Student student = studentRepository.findByStudentId("23070006018").orElseThrow();
        return "Bearer " + jwtService.issueToken(student);
    }

    private String memberToken() {
        Student student = studentRepository.findByStudentId("23070006019").orElseThrow();
        return "Bearer " + jwtService.issueToken(student);
    }

    private String professorAToken() {
        StaffUser professor = staffUserRepository.findByMail("professor@test.com").orElseThrow();
        return "Bearer " + jwtService.issueToken(professor);
    }

    private String professorBToken() {
        StaffUser professor = staffUserRepository.findByMail("professorb@test.com").orElseThrow();
        return "Bearer " + jwtService.issueToken(professor);
    }

    private String adminToken() {
        StaffUser admin = staffUserRepository.findByMail("test@test.com").orElseThrow();
        return "Bearer " + jwtService.issueToken(admin);
    }

    private String submissionBody(String content) {
        return """
                {
                  "markdownContent": "%s"
                }
                """.formatted(content);
    }

    private String commentBody() {
        return """
                {
                  "commentText": "Unauthorized professor comment attempt",
                  "sectionReference": "Introduction"
                }
                """;
    }

    private String createValidSubmissionAndReturnId() throws Exception {
        String responseBody = mockMvc.perform(post("/api/deliverables/{deliverableId}/submissions", validDeliverableId)
                        .header("Authorization", teamLeaderToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionBody("QA setup submission")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(responseBody);

        if (json.has("submissionId")) {
            return json.get("submissionId").asText();
        }

        if (json.has("id")) {
            return json.get("id").asText();
        }

        throw new IllegalStateException("Submission id field not found in response: " + responseBody);
    }

    @Test
    void issue203_validSubmission_shouldReturn201Created() throws Exception {
        mockMvc.perform(post("/api/deliverables/{deliverableId}/submissions", validDeliverableId)
                        .header("Authorization", teamLeaderToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionBody("QA valid submission")))
                .andExpect(status().isCreated());
    }

    @Test
    void issue203_expiredDeadline_shouldReturn400BadRequest() throws Exception {
        mockMvc.perform(post("/api/deliverables/{deliverableId}/submissions", expiredDeliverableId)
                        .header("Authorization", teamLeaderToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionBody("QA expired submission")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void issue203_groupWithoutCommitteeAssignment_shouldReturn400BadRequest() throws Exception {
        mockMvc.perform(post("/api/deliverables/{deliverableId}/submissions", noCommitteeDeliverableId)
                        .header("Authorization", teamLeaderToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionBody("QA no committee submission")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void issue204_adminSubmitAttempt_shouldReturn403Forbidden() throws Exception {
        mockMvc.perform(post("/api/deliverables/{deliverableId}/submissions", validDeliverableId)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionBody("Admin submit attempt")))
                .andExpect(status().isForbidden());
    }

    @Test
    void issue204_standardMemberSubmitAttempt_shouldReturn403Forbidden() throws Exception {
        mockMvc.perform(post("/api/deliverables/{deliverableId}/submissions", validDeliverableId)
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionBody("Member submit attempt")))
                .andExpect(status().isForbidden());
    }

    @Test
    void issue204_standardMemberUpdateAttempt_shouldReturn403Forbidden() throws Exception {
        String submissionId = createValidSubmissionAndReturnId();

        mockMvc.perform(put("/api/submissions/{submissionId}", submissionId)
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionBody("Member update attempt")))
                .andExpect(status().isForbidden());
    }

    @Test
    void issue204_professorBAccessingProfessorASubmission_shouldReturn403Forbidden() throws Exception {
        String submissionId = createValidSubmissionAndReturnId();

        mockMvc.perform(get("/api/submissions/{submissionId}", submissionId)
                        .header("Authorization", professorBToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void issue204_professorBCommentAttempt_shouldReturn403Forbidden() throws Exception {
        String submissionId = createValidSubmissionAndReturnId();

        mockMvc.perform(post("/api/submissions/{submissionId}/comments", submissionId)
                        .header("Authorization", professorBToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void issue204_professorAAccessingAssignedSubmission_shouldReturn200Ok() throws Exception {
        String submissionId = createValidSubmissionAndReturnId();

        mockMvc.perform(get("/api/submissions/{submissionId}", submissionId)
                        .header("Authorization", professorAToken()))
                .andExpect(status().isOk());
    }
}
