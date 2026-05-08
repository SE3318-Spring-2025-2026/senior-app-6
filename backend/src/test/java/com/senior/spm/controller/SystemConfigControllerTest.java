package com.senior.spm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.StaffUser.Role;
import com.senior.spm.entity.Student;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.repository.StudentRepository;
import com.senior.spm.repository.SystemConfigRepository;
import com.senior.spm.service.JWTService;

/**
 * Integration tests for PATCH /api/coordinator/system-config (S4-09 backend coverage, issue #254).
 *
 * Covers:
 *   – Valid coordinator payload → 200, both keys persisted in H2
 *   – Non-coordinator callers (professor, student) → 403
 *   – maxTeamSize: 0 violates @Min(1) constraint → 400
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SystemConfigControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JWTService jwtService;

    @Autowired StaffUserRepository staffUserRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired SystemConfigRepository systemConfigRepository;

    private String coordinatorToken;
    private String professorToken;
    private String studentToken;

    @BeforeEach
    void setUp() {
        cleanAll();

        StaffUser coordinator = staffUserRepository.save(makeStaff("coord@sc-test.com", Role.Coordinator));
        StaffUser professor   = staffUserRepository.save(makeStaff("prof@sc-test.com",  Role.Professor));
        Student   student     = studentRepository.save(makeStudent("33300000001"));

        coordinatorToken = jwtService.issueToken(coordinator);
        professorToken   = jwtService.issueToken(professor);
        studentToken     = jwtService.issueToken(student);
    }

    @AfterEach
    void tearDown() {
        cleanAll();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Happy path — coordinator with valid payload
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Coordinator PATCH with both fields → 200, both values persisted in H2")
    void coordinator_validPayload_returns200AndPersistsBothValues() throws Exception {
        mockMvc.perform(patch("/api/coordinator/system-config")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"activeTermId\":\"2026-SC-TEST\",\"maxTeamSize\":6}"))
                .andExpect(status().isOk());

        // H2 assertion — both config keys must be present with the sent values
        assertThat(systemConfigRepository.findByConfigKey("active_term_id"))
                .isPresent()
                .hasValueSatisfying(c -> assertThat(c.getConfigValue()).isEqualTo("2026-SC-TEST"));

        assertThat(systemConfigRepository.findByConfigKey("max_team_size"))
                .isPresent()
                .hasValueSatisfying(c -> assertThat(c.getConfigValue()).isEqualTo("6"));
    }

    @Test
    @DisplayName("Coordinator PATCH with only activeTermId → 200, only that key persisted")
    void coordinator_onlyActiveTermId_returns200AndPersistsSingleKey() throws Exception {
        mockMvc.perform(patch("/api/coordinator/system-config")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"activeTermId\":\"2026-ONLY-TERM\"}"))
                .andExpect(status().isOk());

        assertThat(systemConfigRepository.findByConfigKey("active_term_id"))
                .isPresent()
                .hasValueSatisfying(c -> assertThat(c.getConfigValue()).isEqualTo("2026-ONLY-TERM"));

        assertThat(systemConfigRepository.findByConfigKey("max_team_size")).isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Non-coordinator callers — 403
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Professor PATCH → 403")
    void professor_returns403() throws Exception {
        mockMvc.perform(patch("/api/coordinator/system-config")
                .header("Authorization", "Bearer " + professorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"activeTermId\":\"2026-SC-TEST\",\"maxTeamSize\":5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Student PATCH → 403")
    void student_returns403() throws Exception {
        mockMvc.perform(patch("/api/coordinator/system-config")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"activeTermId\":\"2026-SC-TEST\",\"maxTeamSize\":5}"))
                .andExpect(status().isForbidden());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Validation — maxTeamSize: 0 violates @Min(1)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("maxTeamSize: 0 → 400 (violates @Min(1))")
    void maxTeamSizeZero_returns400() throws Exception {
        mockMvc.perform(patch("/api/coordinator/system-config")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxTeamSize\":0}"))
                .andExpect(status().isBadRequest());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════

    private void cleanAll() {
        systemConfigRepository.deleteAll();
        studentRepository.deleteAll();
        staffUserRepository.deleteAll();
    }

    private StaffUser makeStaff(String mail, Role role) {
        StaffUser u = new StaffUser();
        u.setMail(mail);
        u.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
        u.setRole(role);
        u.setAdvisorCapacity(5);
        return u;
    }

    private Student makeStudent(String studentId) {
        Student s = new Student();
        s.setStudentId(studentId);
        return s;
    }
}
