package com.senior.spm.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.SystemConfig;
import com.senior.spm.repository.AdvisorRequestRepository;
import com.senior.spm.repository.PasswordResetTokenRepository;
import com.senior.spm.repository.StaffUserRepository;
import com.senior.spm.repository.SystemConfigRepository;

/**
 * Integration tests for advisor capacity endpoints.
 * Issue #245 — Deliverable 2.
 *
 * Endpoints: POST /api/admin/register-professor (capacity field)
 *            PATCH /api/coordinator/advisors/{id}/capacity
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
class AdvisorCapacityControllerTest {

    private static final String TERM_ID = "2026-SPRING-AC-TEST";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StaffUserRepository staffUserRepository;
    @Autowired private SystemConfigRepository systemConfigRepository;
    @Autowired private AdvisorRequestRepository advisorRequestRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;

    private StaffUser admin;
    private StaffUser coordinator;

    @BeforeEach
    void setUp() {
        cleanAll();

        SystemConfig sc = new SystemConfig();
        sc.setConfigKey("active_term_id");
        sc.setConfigValue(TERM_ID);
        systemConfigRepository.save(sc);

        admin       = staffUserRepository.save(makeStaff("admin-ac@test.com",  StaffUser.Role.Admin));
        coordinator = staffUserRepository.save(makeStaff("coord-ac@test.com", StaffUser.Role.Coordinator));
    }

    @AfterEach
    void tearDown() {
        cleanAll();
    }

    private void cleanAll() {
        advisorRequestRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        staffUserRepository.deleteAll();
        systemConfigRepository.deleteAll();
    }

    // ── POST /api/admin/register-professor — capacity field ──────────────────

    @Test
    @DisplayName("Register professor with capacity:3 → advisorCapacity stored as 3")
    void registerProfessor_withCapacity_storesCapacity() throws Exception {
        Map<String, Object> body = Map.of(
                "mail", "newprof-cap@test.com",
                "capacity", 3
        );

        mockMvc.perform(post("/api/admin/register-professor")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        StaffUser saved = staffUserRepository.findByMail("newprof-cap@test.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getAdvisorCapacity()).isEqualTo(3);
    }

    @Test
    @DisplayName("Register professor without capacity → advisorCapacity defaults to 5")
    void registerProfessor_withoutCapacity_defaultsFiveStored() throws Exception {
        Map<String, Object> body = Map.of("mail", "newprof-def@test.com");

        mockMvc.perform(post("/api/admin/register-professor")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        StaffUser saved = staffUserRepository.findByMail("newprof-def@test.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getAdvisorCapacity()).isEqualTo(5);
    }

    // ── PATCH /api/coordinator/advisors/{id}/capacity ─────────────────────────

    @Test
    @DisplayName("PATCH capacity:8 for existing Professor → 200, capacity stored as 8")
    void patchCapacity_existingProfessor_returns200AndStoresValue() throws Exception {
        StaffUser professor = staffUserRepository.save(makeStaff("prof-patch@test.com", StaffUser.Role.Professor));

        Map<String, Object> body = Map.of("capacity", 8);

        mockMvc.perform(patch("/api/coordinator/advisors/{id}/capacity", professor.getId())
                        .with(authentication(coordinatorAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(8));

        StaffUser updated = staffUserRepository.findById(professor.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getAdvisorCapacity()).isEqualTo(8);
    }

    @Test
    @DisplayName("PATCH capacity for non-Professor (Coordinator) → 400 Bad Request")
    void patchCapacity_nonProfessorTarget_returns400() throws Exception {
        // coordinator is Role.Coordinator — not a Professor
        Map<String, Object> body = Map.of("capacity", 5);

        mockMvc.perform(patch("/api/coordinator/advisors/{id}/capacity", coordinator.getId())
                        .with(authentication(coordinatorAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH capacity for unknown UUID → 404 Not Found")
    void patchCapacity_unknownId_returns404() throws Exception {
        Map<String, Object> body = Map.of("capacity", 5);

        mockMvc.perform(patch("/api/coordinator/advisors/{id}/capacity", UUID.randomUUID())
                        .with(authentication(coordinatorAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private StaffUser makeStaff(String mail, StaffUser.Role role) {
        StaffUser u = new StaffUser();
        u.setMail(mail);
        u.setPasswordHash("hash");
        u.setRole(role);
        u.setAdvisorCapacity(5);
        return u;
    }

    private Authentication adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                admin.getId().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Authentication coordinatorAuth() {
        return new UsernamePasswordAuthenticationToken(
                coordinator.getId().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_COORDINATOR")));
    }
}
