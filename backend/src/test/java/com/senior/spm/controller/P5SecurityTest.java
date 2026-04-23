package com.senior.spm.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Verifies SecurityConfig URL-pattern guards for the P5 sprint/grading endpoints.
 *
 * /api/sprints/** → ROLE_STUDENT only
 * /api/advisor/sprints/** → ROLE_PROFESSOR only (via /api/advisor/**)
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
class P5SecurityTest {

    @Autowired
    private MockMvc mockMvc;

    // ── /api/sprints/** → STUDENT only ──────────────────────────────────────

    @Test
    @DisplayName("Professor JWT on GET /api/sprints/active returns 403")
    void professor_getActiveSprint_returns403() throws Exception {
        mockMvc.perform(get("/api/sprints/active")
                .with(authentication(professorAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Coordinator JWT on GET /api/sprints/active returns 403")
    void coordinator_getActiveSprint_returns403() throws Exception {
        mockMvc.perform(get("/api/sprints/active")
                .with(authentication(coordinatorAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin JWT on GET /api/sprints/active returns 403")
    void admin_getActiveSprint_returns403() throws Exception {
        mockMvc.perform(get("/api/sprints/active")
                .with(authentication(adminAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Student JWT on GET /api/sprints/active is not rejected by security (404 from empty DB)")
    void student_getActiveSprint_passesSecurityCheck() throws Exception {
        mockMvc.perform(get("/api/sprints/active")
                .with(authentication(studentAuth())))
                .andExpect(status().isNotFound()); // no sprint in H2 → NotFoundException → 404
    }

    // ── /api/advisor/sprints/** → PROFESSOR only ─────────────────────────────

    @Test
    @DisplayName("Student JWT on GET /api/advisor/sprints/active returns 403")
    void student_getAdvisorActiveSprint_returns403() throws Exception {
        mockMvc.perform(get("/api/advisor/sprints/active")
                .with(authentication(studentAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Student JWT on GET /api/advisor/sprints/{id}/groups returns 403")
    void student_getAdvisorGroupSummaries_returns403() throws Exception {
        mockMvc.perform(get("/api/advisor/sprints/{sprintId}/groups", UUID.randomUUID())
                .with(authentication(studentAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Student JWT on GET /api/advisor/sprints/{id}/groups/{groupId}/tracking returns 403")
    void student_getAdvisorGroupTracking_returns403() throws Exception {
        mockMvc.perform(get("/api/advisor/sprints/{sprintId}/groups/{groupId}/tracking",
                UUID.randomUUID(), UUID.randomUUID())
                .with(authentication(studentAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Student JWT on POST /api/advisor/sprints/{id}/groups/{groupId}/grade returns 403")
    void student_submitGrade_returns403() throws Exception {
        mockMvc.perform(post("/api/advisor/sprints/{sprintId}/groups/{groupId}/grade",
                UUID.randomUUID(), UUID.randomUUID())
                .with(authentication(studentAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pointA_grade\":\"A\",\"pointB_grade\":\"B\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Coordinator JWT on GET /api/advisor/sprints/active returns 403")
    void coordinator_getAdvisorActiveSprint_returns403() throws Exception {
        mockMvc.perform(get("/api/advisor/sprints/active")
                .with(authentication(coordinatorAuth())))
                .andExpect(status().isForbidden());
    }

    // ── /api/groups/{id}/sprints/{id}/tracking → any authenticated user ──────

    @Test
    @DisplayName("Unauthenticated request on GET /api/groups/{id}/sprints/{id}/tracking returns 401")
    void unauthenticated_studentTracking_returns401() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/sprints/{sprintId}/tracking",
                UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── Auth helpers ─────────────────────────────────────────────────────────

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
