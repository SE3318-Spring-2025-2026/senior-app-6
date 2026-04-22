package com.senior.spm.service;

import com.senior.spm.entity.SprintTrackingLog.AiValidationResult;
import com.senior.spm.repository.SystemConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AiValidationService — non-HTTP branches only.
 * HTTP-response branches (PASS/WARN/FAIL/timeout/5xx) are covered in AiValidationWireMockTest.
 * Issue: #151
 */
@ExtendWith(MockitoExtension.class)
class AiValidationServiceTest {

    @Mock
    private SystemConfigRepository systemConfigRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private RestClient llmClient;

    private AiValidationService service;

    @BeforeEach
    void setUp() {
        service = new AiValidationService(systemConfigRepository, encryptionService, llmClient);
    }

    // ── Empty / null comment list → SKIPPED, zero HTTP calls ─────────────────

    @Test
    void validatePRReview_nullComments_returnsSkipped() {
        AiValidationResult result = service.validatePRReview(null);

        assertThat(result).isEqualTo(AiValidationResult.SKIPPED);
        verify(llmClient, never()).post();
    }

    @Test
    void validatePRReview_emptyComments_returnsSkipped() {
        AiValidationResult result = service.validatePRReview(List.of());

        assertThat(result).isEqualTo(AiValidationResult.SKIPPED);
        verify(llmClient, never()).post();
    }

    // ── Missing llm_api_key → WARN, zero HTTP calls ───────────────────────────

    @Test
    void validatePRReview_missingLlmKey_returnsWarn_noHttpCall() {
        when(systemConfigRepository.findById("llm_api_key")).thenReturn(Optional.empty());

        AiValidationResult result = service.validatePRReview(List.of("LGTM", "Looks good"));

        assertThat(result).isEqualTo(AiValidationResult.WARN);
        verify(llmClient, never()).post();
    }

    // ── Empty list guard is checked before DB lookup ──────────────────────────

    @Test
    void validatePRReview_emptyList_doesNotQueryRepository() {
        service.validatePRReview(List.of());

        verify(systemConfigRepository, never()).findById("llm_api_key");
        verify(llmClient, never()).post();
    }

}
