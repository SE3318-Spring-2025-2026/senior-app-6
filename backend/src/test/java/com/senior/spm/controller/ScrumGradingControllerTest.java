package com.senior.spm.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.Sprint;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.ScrumGradeRepository;
import com.senior.spm.repository.SprintRepository;
import com.senior.spm.repository.StaffUserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
class ScrumGradingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StaffUserRepository staffUserRepository;
    @Autowired private SprintRepository sprintRepository;
    @Autowired private ProjectGroupRepository projectGroupRepository;
    @Autowired private ScrumGradeRepository scrumGradeRepository;

    private StaffUser advisor;
    private Sprint sprint;
    private ProjectGroup group;

    @BeforeEach
    void setUp() {
        scrumGradeRepository.deleteAll();
        projectGroupRepository.deleteAll();
        sprintRepository.deleteAll();
        staffUserRepository.deleteAll();

        advisor = staffUserRepository.save(professor("advisor@test.com"));
        sprint  = sprintRepository.save(sprint(LocalDate.now().minusDays(5), LocalDate.now().plusDays(9)));
        group   = projectGroupRepository.save(groupWithAdvisor("TeamAlpha", advisor));
    }

    @AfterEach
    void tearDown() {
        scrumGradeRepository.deleteAll();
        projectGroupRepository.deleteAll();
        sprintRepository.deleteAll();
        staffUserRepository.deleteAll();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  POST /api/advisor/sprints/{sprintId}/groups/{groupId}/grade
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("First submission returns 201 and JSON fields are pointA_grade / pointB_grade (snake_case)")
    void firstSubmission_returns201_andJsonFieldsAreSnakeCase() throws Exception {
        mockMvc.perform(post("/api/advisor/sprints/{sprintId}/groups/{groupId}/grade",
                        sprint.getId(), group.getId())
                .with(authentication(professorAuth(advisor)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pointA_grade\":\"A\",\"pointB_grade\":\"B\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pointA_grade").value("A"))
                .andExpect(jsonPath("$.pointB_grade").value("B"))
                .andExpect(jsonPath("$.gradedAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.groupId").value(group.getId().toString()))
                .andExpect(jsonPath("$.sprintId").value(sprint.getId().toString()))
                .andExpect(jsonPath("$.advisorId").value(advisor.getId().toString()));
    }

    @Test
    @DisplayName("Second submission (upsert) returns 200 with non-null updatedAt and updated grades")
    void secondSubmission_returns200_andUpdatedAtIsPresent() throws Exception {
        mockMvc.perform(post("/api/advisor/sprints/{sprintId}/groups/{groupId}/grade",
                        sprint.getId(), group.getId())
                .with(authentication(professorAuth(advisor)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pointA_grade\":\"A\",\"pointB_grade\":\"B\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/advisor/sprints/{sprintId}/groups/{groupId}/grade",
                        sprint.getId(), group.getId())
                .with(authentication(professorAuth(advisor)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pointA_grade\":\"C\",\"pointB_grade\":\"D\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointA_grade").value("C"))
                .andExpect(jsonPath("$.pointB_grade").value("D"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    @DisplayName("Submission with wrong advisor returns 403")
    void submitGrade_wrongAdvisor_returns403() throws Exception {
        StaffUser otherAdvisor = staffUserRepository.save(professor("other@test.com"));

        mockMvc.perform(post("/api/advisor/sprints/{sprintId}/groups/{groupId}/grade",
                        sprint.getId(), group.getId())
                .with(authentication(professorAuth(otherAdvisor)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pointA_grade\":\"A\",\"pointB_grade\":\"A\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Submission with invalid grade value returns 400")
    void submitGrade_invalidGradeValue_returns400() throws Exception {
        mockMvc.perform(post("/api/advisor/sprints/{sprintId}/groups/{groupId}/grade",
                        sprint.getId(), group.getId())
                .with(authentication(professorAuth(advisor)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pointA_grade\":\"INVALID\",\"pointB_grade\":\"A\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Submission with missing grade field returns 400")
    void submitGrade_missingRequiredField_returns400() throws Exception {
        mockMvc.perform(post("/api/advisor/sprints/{sprintId}/groups/{groupId}/grade",
                        sprint.getId(), group.getId())
                .with(authentication(professorAuth(advisor)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pointA_grade\":\"A\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Submission for non-existent group returns 404")
    void submitGrade_groupNotFound_returns404() throws Exception {
        mockMvc.perform(post("/api/advisor/sprints/{sprintId}/groups/{groupId}/grade",
                        sprint.getId(), UUID.randomUUID())
                .with(authentication(professorAuth(advisor)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pointA_grade\":\"A\",\"pointB_grade\":\"B\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Submission for non-existent sprint returns 404")
    void submitGrade_sprintNotFound_returns404() throws Exception {
        mockMvc.perform(post("/api/advisor/sprints/{sprintId}/groups/{groupId}/grade",
                        UUID.randomUUID(), group.getId())
                .with(authentication(professorAuth(advisor)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pointA_grade\":\"A\",\"pointB_grade\":\"B\"}"))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GET /api/advisor/sprints/{sprintId}/groups/{groupId}/grade
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET grade after submission returns 200 with correct field values")
    void getGrade_afterSubmission_returns200WithFields() throws Exception {
        mockMvc.perform(post("/api/advisor/sprints/{sprintId}/groups/{groupId}/grade",
                        sprint.getId(), group.getId())
                .with(authentication(professorAuth(advisor)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pointA_grade\":\"B\",\"pointB_grade\":\"C\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/advisor/sprints/{sprintId}/groups/{groupId}/grade",
                        sprint.getId(), group.getId())
                .with(authentication(professorAuth(advisor))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointA_grade").value("B"))
                .andExpect(jsonPath("$.pointB_grade").value("C"))
                .andExpect(jsonPath("$.gradedAt").isNotEmpty());
    }

    @Test
    @DisplayName("GET grade when no grade submitted returns 404 (not 200 empty)")
    void getGrade_noGradeSubmitted_returns404() throws Exception {
        mockMvc.perform(get("/api/advisor/sprints/{sprintId}/groups/{groupId}/grade",
                        sprint.getId(), group.getId())
                .with(authentication(professorAuth(advisor))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("GET grade with wrong advisor returns 403")
    void getGrade_wrongAdvisor_returns403() throws Exception {
        StaffUser otherAdvisor = staffUserRepository.save(professor("other2@test.com"));

        mockMvc.perform(get("/api/advisor/sprints/{sprintId}/groups/{groupId}/grade",
                        sprint.getId(), group.getId())
                .with(authentication(professorAuth(otherAdvisor))))
                .andExpect(status().isForbidden());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private StaffUser professor(String mail) {
        StaffUser u = new StaffUser();
        u.setMail(mail);
        u.setPasswordHash("hash");
        u.setRole(StaffUser.Role.Professor);
        u.setAdvisorCapacity(5);
        return u;
    }

    private Sprint sprint(LocalDate start, LocalDate end) {
        Sprint s = new Sprint();
        s.setStartDate(start);
        s.setEndDate(end);
        s.setStoryPointTarget(40);
        return s;
    }

    private ProjectGroup groupWithAdvisor(String name, StaffUser adv) {
        ProjectGroup g = new ProjectGroup();
        g.setGroupName(name);
        g.setTermId("2026-SPRING");
        g.setStatus(ProjectGroup.GroupStatus.ADVISOR_ASSIGNED);
        g.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));
        g.setAdvisor(adv);
        return g;
    }

    private Authentication professorAuth(StaffUser professor) {
        return new UsernamePasswordAuthenticationToken(
                professor.getId().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_PROFESSOR")));
    }
}
