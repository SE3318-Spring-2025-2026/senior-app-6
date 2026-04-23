package com.senior.spm.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.senior.spm.entity.SystemConfig;
import com.senior.spm.entity.SprintTrackingLog.AiValidationResult;
import com.senior.spm.repository.SystemConfigRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WireMock integration tests for AiValidationService — covers all HTTP-response branches.
 * Non-HTTP branches (SKIPPED, missing key) are covered in AiValidationServiceTest.
 * Issue: #151
 */
class AiValidationWireMockTest {

    private static WireMockServer wireMockServer;

    private AiValidationService service;
    private SystemConfigRepository systemConfigRepository;

    private static final String FAKE_ENCRYPTED_KEY = "fake-encrypted-key";
    private static final String FAKE_PLAIN_KEY = "AIzaSyFakeKeyForTests";
    private static final String GEMINI_PATH = "/v1beta/models/gemini-2.5-flash-lite:generateContent";

    @BeforeAll
    static void startServer() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterAll
    static void stopServer() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();

        systemConfigRepository = mock(SystemConfigRepository.class);
        SystemConfig config = new SystemConfig();
        config.setConfigKey("llm_api_key");
        config.setConfigValue(FAKE_ENCRYPTED_KEY);
        when(systemConfigRepository.findById("llm_api_key")).thenReturn(Optional.of(config));

        EncryptionService encryptionService = mock(EncryptionService.class);
        when(encryptionService.decrypt(any())).thenReturn(FAKE_PLAIN_KEY);

        // timeoutSeconds=1 keeps timeout tests fast (WireMock delay set to 2s)
        service = new AiValidationService(
                systemConfigRepository,
                encryptionService,
                RestClient.builder(),
                wireMockServer.baseUrl(),
                "gemini-2.5-flash-lite",
                1
        );
    }

    // ── Happy path — LLM returns PASS / WARN / FAIL ───────────────────────────

    @Test
    void validatePRReview_geminiReturnsPass_returnsPass() {
        stubGemini(200, geminiBody("PASS"));

        AiValidationResult result = service.validatePRReview(List.of("Addressed nit.", "Logic looks correct."));

        assertThat(result).isEqualTo(AiValidationResult.PASS);
    }

    @Test
    void validatePRReview_geminiReturnsWarn_returnsWarn() {
        stubGemini(200, geminiBody("WARN"));

        AiValidationResult result = service.validatePRReview(List.of("LGTM"));

        assertThat(result).isEqualTo(AiValidationResult.WARN);
    }

    @Test
    void validatePRReview_geminiReturnsFail_returnsFail() {
        stubGemini(200, geminiBody("FAIL"));

        AiValidationResult result = service.validatePRReview(List.of("👍"));

        assertThat(result).isEqualTo(AiValidationResult.FAIL);
    }

    // ── Unrecognised LLM output → WARN ────────────────────────────────────────

    @Test
    void validatePRReview_geminiReturnsGarbledOutput_returnsWarn() {
        stubGemini(200, geminiBody("The review looks fine to me."));

        AiValidationResult result = service.validatePRReview(List.of("Some comment"));

        assertThat(result).isEqualTo(AiValidationResult.WARN);
    }

    // ── LLM responds with lowercase — still maps correctly ───────────────────

    @Test
    void validatePRReview_geminiReturnsLowercasePass_returnsPass() {
        stubGemini(200, geminiBody("pass"));

        AiValidationResult result = service.validatePRReview(List.of("Detailed review"));

        assertThat(result).isEqualTo(AiValidationResult.PASS);
    }

    // ── HTTP error branches → WARN, no exception propagated ──────────────────

    @Test
    void validatePRReview_gemini500_returnsWarn_noException() {
        stubGemini(500, "{\"error\":{\"message\":\"Internal server error\"}}");

        assertThatCode(() -> {
            AiValidationResult result = service.validatePRReview(List.of("Some review"));
            assertThat(result).isEqualTo(AiValidationResult.WARN);
        }).doesNotThrowAnyException();
    }

    @Test
    void validatePRReview_gemini503_returnsWarn_noException() {
        stubGemini(503, "{\"error\":{\"message\":\"Service unavailable\"}}");

        assertThatCode(() -> {
            AiValidationResult result = service.validatePRReview(List.of("Some review"));
            assertThat(result).isEqualTo(AiValidationResult.WARN);
        }).doesNotThrowAnyException();
    }

    // ── Timeout → WARN, no exception propagated ───────────────────────────────

    @Test
    void validatePRReview_geminiTimeout_returnsWarn_noException() {
        // 2s delay exceeds the 1s timeout configured in setUp()
        wireMockServer.stubFor(post(urlPathEqualTo(GEMINI_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(2000)
                        .withBody(geminiBody("PASS"))));

        assertThatCode(() -> {
            AiValidationResult result = service.validatePRReview(List.of("Some review"));
            assertThat(result).isEqualTo(AiValidationResult.WARN);
        }).doesNotThrowAnyException();
    }

    // ── Empty candidates list in response → WARN ─────────────────────────────

    @Test
    void validatePRReview_emptyCandidates_returnsWarn() {
        stubGemini(200, "{\"candidates\":[]}");

        AiValidationResult result = service.validatePRReview(List.of("Some review"));

        assertThat(result).isEqualTo(AiValidationResult.WARN);
    }

    // ── API key is passed as query parameter ──────────────────────────────────

    @Test
    void validatePRReview_sendsApiKeyAsQueryParam() {
        stubGemini(200, geminiBody("PASS"));

        service.validatePRReview(List.of("Some review"));

        wireMockServer.verify(1,
                WireMock.postRequestedFor(urlPathEqualTo(GEMINI_PATH))
                        .withQueryParam("key", WireMock.equalTo(FAKE_PLAIN_KEY)));
    }

    // ── Content-Type header is set to application/json ────────────────────────

    @Test
    void validatePRReview_setsJsonContentType() {
        stubGemini(200, geminiBody("PASS"));

        service.validatePRReview(List.of("Some review"));

        wireMockServer.verify(1,
                WireMock.postRequestedFor(urlPathEqualTo(GEMINI_PATH))
                        .withHeader("Content-Type", WireMock.containing(MediaType.APPLICATION_JSON_VALUE)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubGemini(int status, String body) {
        wireMockServer.stubFor(post(urlPathEqualTo(GEMINI_PATH))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(body)));
    }

    private static String geminiBody(String text) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + text + "\"}]}}]}";
    }
}
