package com.senior.spm.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.Student;
import com.senior.spm.repository.DeliverableSubmissionRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.repository.StudentRepository;
import com.senior.spm.service.JWTService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
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
    private ObjectMapper objectMapper;

    private static final UUID VALID_DELIVERABLE_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private static final UUID EXPIRED_DELIVERABLE_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaab");

    private static final UUID NO_COMMITTEE_DELIVERABLE_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaac");

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
        String responseBody = mockMvc.perform(post("/api/deliverables/{deliverableId}/submissions", VALID_DELIVERABLE_ID)
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
        mockMvc.perform(post("/api/deliverables/{deliverableId}/submissions", VALID_DELIVERABLE_ID)
                        .header("Authorization", teamLeaderToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionBody("QA valid submission")))
                .andExpect(status().isCreated());
    }

    @Test
    void issue203_expiredDeadline_shouldReturn400BadRequest() throws Exception {
        mockMvc.perform(post("/api/deliverables/{deliverableId}/submissions", EXPIRED_DELIVERABLE_ID)
                        .header("Authorization", teamLeaderToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionBody("QA expired submission")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void issue203_groupWithoutCommitteeAssignment_shouldReturn400BadRequest() throws Exception {
        mockMvc.perform(post("/api/deliverables/{deliverableId}/submissions", NO_COMMITTEE_DELIVERABLE_ID)
                        .header("Authorization", teamLeaderToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionBody("QA no committee submission")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void issue204_adminSubmitAttempt_shouldReturn403Forbidden() throws Exception {
        mockMvc.perform(post("/api/deliverables/{deliverableId}/submissions", VALID_DELIVERABLE_ID)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionBody("Admin submit attempt")))
                .andExpect(status().isForbidden());
    }

    @Test
    void issue204_standardMemberSubmitAttempt_shouldReturn403Forbidden() throws Exception {
        mockMvc.perform(post("/api/deliverables/{deliverableId}/submissions", VALID_DELIVERABLE_ID)
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