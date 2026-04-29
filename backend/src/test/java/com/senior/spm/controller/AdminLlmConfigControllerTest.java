package com.senior.spm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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

import com.senior.spm.entity.SystemConfig;
import com.senior.spm.repository.SystemConfigRepository;
import com.senior.spm.service.AiValidationService;
import com.senior.spm.service.EncryptionService;

/**
 * Integration tests for GET/PUT /api/admin/llm-config.
 * Issue: #228
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test.properties")
class AdminLlmConfigControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SystemConfigRepository systemConfigRepository;
    @Autowired private EncryptionService encryptionService;
    @Autowired private AiValidationService aiValidationService;

    @BeforeEach
    void setUp() {
        systemConfigRepository.deleteById("llm_api_key");
        aiValidationService.invalidateKeyCache();
    }

    @AfterEach
    void tearDown() {
        systemConfigRepository.deleteById("llm_api_key");
        aiValidationService.invalidateKeyCache();
    }

    // ── GET /api/admin/llm-config ─────────────────────────────────────────────

    @Test
    @DisplayName("GET llm-config with no row returns configured=false and null maskedKey")
    void getLlmConfig_noRow_returnsNotConfigured() throws Exception {
        mockMvc.perform(get("/api/admin/llm-config")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.maskedKey").doesNotExist());
    }

    @Test
    @DisplayName("GET llm-config with row present returns configured=true and masked key")
    void getLlmConfig_rowPresent_returnsMaskedKey() throws Exception {
        saveEncryptedKey("AIzaSyFakeKeyForTests");

        mockMvc.perform(get("/api/admin/llm-config")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.maskedKey").value("AIza...ests"));
    }

    @Test
    @DisplayName("GET llm-config with short key (<=8 chars) returns '****'")
    void getLlmConfig_shortKey_returnsFourStars() throws Exception {
        saveEncryptedKey("short");

        mockMvc.perform(get("/api/admin/llm-config")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.maskedKey").value("****"));
    }

    @Test
    @DisplayName("GET llm-config never returns raw key")
    void getLlmConfig_neverReturnsRawKey() throws Exception {
        String rawKey = "AIzaSyFakeKeyForTests";
        saveEncryptedKey(rawKey);

        String response = mockMvc.perform(get("/api/admin/llm-config")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain(rawKey);
    }

    // ── PUT /api/admin/llm-config ─────────────────────────────────────────────

    @Test
    @DisplayName("PUT with valid key saves encrypted row and clears cache")
    void putLlmConfig_validKey_savesAndClearsCache() throws Exception {
        mockMvc.perform(put("/api/admin/llm-config")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"AIzaSyNewKeyForTests\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("LLM API key updated successfully"));

        SystemConfig saved = systemConfigRepository.findById("llm_api_key").orElseThrow();
        String decrypted = encryptionService.decrypt(saved.getConfigValue());
        assertThat(decrypted).isEqualTo("AIzaSyNewKeyForTests");
        assertThat(saved.getConfigValue()).isNotEqualTo("AIzaSyNewKeyForTests");
    }

    @Test
    @DisplayName("PUT with blank apiKey returns 400")
    void putLlmConfig_blankKey_returns400() throws Exception {
        mockMvc.perform(put("/api/admin/llm-config")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT with missing apiKey field returns 400")
    void putLlmConfig_missingField_returns400() throws Exception {
        mockMvc.perform(put("/api/admin/llm-config")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT updates existing row (upsert)")
    void putLlmConfig_updatesExistingRow() throws Exception {
        saveEncryptedKey("AIzaSyOldKey12345678");

        mockMvc.perform(put("/api/admin/llm-config")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"AIzaSyNewKey12345678\"}"))
                .andExpect(status().isOk());

        SystemConfig saved = systemConfigRepository.findById("llm_api_key").orElseThrow();
        assertThat(encryptionService.decrypt(saved.getConfigValue())).isEqualTo("AIzaSyNewKey12345678");
    }

    // ── RBAC ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Professor GET /api/admin/llm-config returns 403")
    void professor_getLlmConfig_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/llm-config")
                        .with(authentication(professorAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Coordinator GET /api/admin/llm-config returns 403")
    void coordinator_getLlmConfig_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/llm-config")
                        .with(authentication(coordinatorAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Professor PUT /api/admin/llm-config returns 403")
    void professor_putLlmConfig_returns403() throws Exception {
        mockMvc.perform(put("/api/admin/llm-config")
                        .with(authentication(professorAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"AIzaSyFakeKey\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Coordinator PUT /api/admin/llm-config returns 403")
    void coordinator_putLlmConfig_returns403() throws Exception {
        mockMvc.perform(put("/api/admin/llm-config")
                        .with(authentication(coordinatorAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"AIzaSyFakeKey\"}"))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void saveEncryptedKey(String plainKey) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey("llm_api_key");
        config.setConfigValue(encryptionService.encrypt(plainKey));
        systemConfigRepository.save(config);
    }

    private Authentication adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                java.util.UUID.randomUUID().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Authentication professorAuth() {
        return new UsernamePasswordAuthenticationToken(
                java.util.UUID.randomUUID().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_PROFESSOR")));
    }

    private Authentication coordinatorAuth() {
        return new UsernamePasswordAuthenticationToken(
                java.util.UUID.randomUUID().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_COORDINATOR")));
    }
}
