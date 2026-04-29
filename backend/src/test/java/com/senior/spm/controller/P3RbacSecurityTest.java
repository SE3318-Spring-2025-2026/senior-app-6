package com.senior.spm.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

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

/**
 * Verifies that SecurityConfig URL-pattern role guards reject requests from the wrong role
 * before any controller method is invoked.
 *
 * /api/advisors and /api/groups/{id}/advisor-request → ROLE_STUDENT only
 * /api/advisor/** → ROLE_PROFESSOR only
 * /api/coordinator/** → ROLE_COORDINATOR only
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
class P3RbacSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    // ── Staff/Admin hitting student-only endpoints ───────────────────────────

    @Test
    @DisplayName("Professor JWT on GET /api/advisor returns 403")
    void professor_getAvailableAdvisors_returns403() throws Exception {
        mockMvc.perform(get("/api/advisor")
                .with(authentication(professorAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Coordinator JWT on GET /api/advisor returns 403")
    void coordinator_getAvailableAdvisors_returns403() throws Exception {
        mockMvc.perform(get("/api/advisor")
                .with(authentication(coordinatorAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Professor JWT on POST /api/groups/{id}/advisor-request returns 403")
    void professor_sendAdvisorRequest_returns403() throws Exception {
        mockMvc.perform(post("/api/groups/{id}/advisor-request", UUID.randomUUID())
                .with(authentication(professorAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"advisorId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Coordinator JWT on GET /api/groups/{id}/advisor-request returns 403")
    void coordinator_getAdvisorRequest_returns403() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/advisor-request", UUID.randomUUID())
                .with(authentication(coordinatorAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin JWT on DELETE /api/groups/{id}/advisor-request returns 403")
    void admin_cancelAdvisorRequest_returns403() throws Exception {
        mockMvc.perform(delete("/api/groups/{id}/advisor-request", UUID.randomUUID())
                .with(authentication(adminAuth())))
                .andExpect(status().isForbidden());
    }

    // ── Student hitting professor-only endpoints (/api/advisor/**) ─────────────

    @Test
    @DisplayName("Student JWT on GET /api/advisor/requests returns 403")
    void student_getAdvisorRequests_returns403() throws Exception {
        mockMvc.perform(get("/api/advisor/requests")
                .with(authentication(studentAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Student JWT on GET /api/advisor/requests/{id} returns 403")
    void student_getAdvisorRequestDetail_returns403() throws Exception {
        mockMvc.perform(get("/api/advisor/requests/{id}", UUID.randomUUID())
                .with(authentication(studentAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Student JWT on PATCH /api/advisor/requests/{id}/respond returns 403")
    void student_respondToAdvisorRequest_returns403() throws Exception {
        mockMvc.perform(patch("/api/advisor/requests/{id}/respond", UUID.randomUUID())
                .with(authentication(studentAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Coordinator JWT on GET /api/advisor/requests returns 403")
    void coordinator_getAdvisorRequests_returns403() throws Exception {
        mockMvc.perform(get("/api/advisor/requests")
                .with(authentication(coordinatorAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin JWT on PATCH /api/advisor/requests/{id}/respond returns 403")
    void admin_respondToAdvisorRequest_returns403() throws Exception {
        mockMvc.perform(patch("/api/advisor/requests/{id}/respond", UUID.randomUUID())
                .with(authentication(adminAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\":true}"))
                .andExpect(status().isForbidden());
    }

    // ── Student hitting coordinator-only endpoints (/api/coordinator/**) ────────

    @Test
    @DisplayName("Student JWT on GET /api/coordinator/advisors returns 403")
    void student_getCoordinatorAdvisors_returns403() throws Exception {
        mockMvc.perform(get("/api/coordinator/advisors")
                .with(authentication(studentAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Student JWT on PATCH /api/coordinator/groups/{id}/advisor returns 403")
    void student_assignAdvisor_returns403() throws Exception {
        mockMvc.perform(patch("/api/coordinator/groups/{id}/advisor", UUID.randomUUID())
                .with(authentication(studentAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"ASSIGN\",\"advisorId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Student JWT on POST /api/coordinator/sanitize returns 403")
    void student_triggerSanitization_returns403() throws Exception {
        mockMvc.perform(post("/api/coordinator/sanitize")
                .with(authentication(studentAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"force\":true}"))
                .andExpect(status().isForbidden());
    }

    // ── Professor hitting coordinator-only endpoints (/api/coordinator/**) ──────

    @Test
    @DisplayName("Professor JWT on GET /api/coordinator/advisors returns 403")
    void professor_getCoordinatorAdvisors_returns403() throws Exception {
        mockMvc.perform(get("/api/coordinator/advisors")
                .with(authentication(professorAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Professor JWT on PATCH /api/coordinator/groups/{id}/advisor returns 403")
    void professor_assignAdvisor_returns403() throws Exception {
        mockMvc.perform(patch("/api/coordinator/groups/{id}/advisor", UUID.randomUUID())
                .with(authentication(professorAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"ASSIGN\",\"advisorId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Professor JWT on POST /api/coordinator/sanitize returns 403")
    void professor_triggerSanitization_returns403() throws Exception {
        mockMvc.perform(post("/api/coordinator/sanitize")
                .with(authentication(professorAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"force\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin JWT on PATCH /api/coordinator/groups/{id}/advisor returns 403")
    void admin_assignAdvisor_returns403() throws Exception {
        mockMvc.perform(patch("/api/coordinator/groups/{id}/advisor", UUID.randomUUID())
                .with(authentication(adminAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"ASSIGN\",\"advisorId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    // ── Auth helpers ────────────────────────────────────────────────────────────

    private Authentication studentAuth() {
        return new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }

    private Authentication professorAuth() {
        return new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_PROFESSOR")));
    }

    private Authentication coordinatorAuth() {
        return new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_COORDINATOR")));
    }

    private Authentication adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
