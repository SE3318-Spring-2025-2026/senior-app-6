package com.senior.spm.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.senior.spm.entity.SprintTrackingLog.AiValidationResult;
import com.senior.spm.repository.SystemConfigRepository;
import com.senior.spm.service.dto.GithubFileDiffDto;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiValidationService {

    // ── Static constants ──────────────────────────────────────────────────────
    private static final String LLM_KEY_CONFIG  = "llm_api_key";
    private static final String PR_REVIEW_PROMPT =
            "Review the following PR review comments and respond with only one word: " +
            "PASS if the review is substantive, " +
            "WARN if it is minimal or superficial, " +
            "FAIL if it is empty or purely cosmetic.\n\n";
    // Section labels are included in the constant so the assembled prompt reads:
    // [instruction] Issue description: [description] Code diff: [patches]
    private static final String DIFF_VALIDATION_PROMPT =
            "Compare the following issue description and code diff, then respond with only one word: " +
            "PASS if the changes clearly implement the issue, " +
            "WARN if the match is partial or unclear, " +
            "FAIL if the changes are unrelated to the issue.\n\n" +
            "Issue description:\n";
    private static final int  MAX_ATTEMPTS    = 3;
    private static final long INITIAL_RETRY_MS = 5_000;  // 5 s → 10 s → 20 s
    private static final long MAX_RETRY_MS     = 60_000; // cap at 60 s

    // ── Instance fields ───────────────────────────────────────────────────────
    private final SystemConfigRepository systemConfigRepository;
    private final EncryptionService encryptionService;
    private final RestClient llmClient;
    private final String geminiPath;
    private final AtomicReference<String> cachedApiKey = new AtomicReference<>();

    // Spring-managed constructor — @Value injects properties from application.properties
    @Autowired
    public AiValidationService(
            SystemConfigRepository systemConfigRepository,
            EncryptionService encryptionService,
            RestClient.Builder restClientBuilder,
            @Value("${llm.api.base-url}") String llmBaseUrl,
            @Value("${llm.api.model:gemini-2.5-flash-lite}") String llmModel,
            @Value("${llm.api.timeout-seconds:10}") int timeoutSeconds) {

        this.systemConfigRepository = systemConfigRepository;
        this.encryptionService = encryptionService;
        this.geminiPath = "/v1beta/models/" + llmModel + ":generateContent?key={key}";

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());

        this.llmClient = restClientBuilder.clone()
                .baseUrl(llmBaseUrl)
                .requestFactory(factory)
                .build();
    }

    // Package-private constructor for unit tests — accepts a pre-built RestClient mock
    AiValidationService(SystemConfigRepository systemConfigRepository,
                        EncryptionService encryptionService,
                        RestClient llmClient) {
        this.systemConfigRepository = systemConfigRepository;
        this.encryptionService = encryptionService;
        this.llmClient = llmClient;
        this.geminiPath = "/v1beta/models/gemini-2.5-flash-lite:generateContent?key={key}";
    }

    @PostConstruct
    void init() {
        if (systemConfigRepository.findById(LLM_KEY_CONFIG).isEmpty()) {
            log.warn("llm_api_key not found in system_config — AI validation will default to WARN");
        }
    }

    /**
     * Validates the quality of PR review comments using Gemini Flash Lite.
     *
     * @param reviewComments list of PR review comment bodies for a merged PR
     * @return SKIPPED if list is empty/null; PASS/WARN/FAIL per LLM judgement;
     *         WARN on any error (timeout, 5xx, parse failure, missing key) — never throws
     */
    public AiValidationResult validatePRReview(List<String> reviewComments) {
        if (reviewComments == null || reviewComments.isEmpty()) {
            return AiValidationResult.SKIPPED;
        }

        try {
            String apiKey = resolveApiKey();
            if (apiKey == null) return AiValidationResult.WARN;

            String promptText = PR_REVIEW_PROMPT + String.join("\n", reviewComments);
            var request = new GeminiRequest(
                    List.of(new GeminiContent(List.of(new GeminiPart(promptText)))),
                    new GeminiRequest.GenerationConfig(10, 0.0)
            );

            return callWithRetry(request, apiKey);

        } catch (ResourceAccessException ex) {
            log.warn("LLM call timed out or connection failed — returning WARN: {}", ex.getMessage());
            return AiValidationResult.WARN;
        } catch (Exception ex) {
            log.warn("Unexpected error during AI PR review validation — returning WARN: {}", ex.getMessage());
            return AiValidationResult.WARN;
        }
    }

    // Reads the key from DB on first call, then serves from cache on subsequent calls.
    private String resolveApiKey() {
        String cached = cachedApiKey.get();
        if (cached != null) return cached;

        var configOpt = systemConfigRepository.findById(LLM_KEY_CONFIG);
        if (configOpt.isEmpty()) {
            log.warn("llm_api_key missing from system_config — returning WARN");
            return null;
        }

        String decrypted = encryptionService.decrypt(configOpt.get().getConfigValue());
        cachedApiKey.set(decrypted);
        return decrypted;
    }

    private AiValidationResult callWithRetry(GeminiRequest request, String apiKey) {
        long delayMs = INITIAL_RETRY_MS;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                GeminiResponse response = llmClient.post()
                        .uri(geminiPath, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(GeminiResponse.class);
                return parseResult(response);

            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode().value() == 429 && attempt < MAX_ATTEMPTS) {
                    long wait = retryDelayMs(ex.getResponseBodyAsString(), delayMs);
                    log.warn("LLM returned 429 (attempt {}/{}) — retrying in {}ms", attempt, MAX_ATTEMPTS, wait);
                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return AiValidationResult.WARN;
                    }
                    delayMs = Math.min(delayMs * 2, MAX_RETRY_MS);
                } else {
                    log.warn("LLM returned HTTP {} — returning WARN", ex.getStatusCode());
                    return AiValidationResult.WARN;
                }
            }
        }
        return AiValidationResult.WARN;
    }

    private static long retryDelayMs(String body, long fallbackMs) {
        try {
            // Gemini embeds: "retryDelay": "28s"  or  "retryDelay": "28.145s"
            int idx = body.indexOf("\"retryDelay\"");
            if (idx == -1) return fallbackMs;
            int start = body.indexOf('"', idx + 13) + 1;
            int end   = body.indexOf('"', start);
            String raw = body.substring(start, end).replace("s", "").trim();
            long suggested = (long) (Double.parseDouble(raw) * 1_000);
            return Math.min(suggested + 1_000, MAX_RETRY_MS);
        } catch (Exception e) {
            return fallbackMs;
        }
    }

    private AiValidationResult parseResult(GeminiResponse response) {
        try {
            String text = response.candidates()
                    .get(0).content().parts().get(0).text()
                    .trim().toUpperCase();

            return switch (text) {
                case "PASS" -> AiValidationResult.PASS;
                case "WARN" -> AiValidationResult.WARN;
                case "FAIL" -> AiValidationResult.FAIL;
                default -> {
                    log.warn("Unrecognised LLM output '{}' — defaulting to WARN", text);
                    yield AiValidationResult.WARN;
                }
            };
        } catch (Exception ex) {
            log.warn("Failed to parse LLM response structure — defaulting to WARN");
            return AiValidationResult.WARN;
        }
    }

    /**
     * Validates whether the code diff implements the JIRA issue using Gemini Flash Lite.
     *
     * @param issueDescription JIRA issue description body
     * @param fileDiffs        list of file diffs from the merged PR
     * @return SKIPPED if fileDiffs is empty/null, issueDescription is blank/null,
     *         or all patches are null (e.g. binary-only PR);
     *         PASS/WARN/FAIL per LLM judgement;
     *         WARN on any error (timeout, 5xx, parse failure, missing key) — never throws
     */
    public AiValidationResult validateIssueDiff(String issueDescription, List<GithubFileDiffDto> fileDiffs) {
        if (fileDiffs == null || fileDiffs.isEmpty()) {
            return AiValidationResult.SKIPPED;
        }
        if (issueDescription == null || issueDescription.isBlank()) {
            return AiValidationResult.SKIPPED;
        }

        // Filter null patches upfront — GithubFileDiffDto.patch is null for binary/oversized files.
        // If no usable patch text remains, there is nothing meaningful to validate.
        String patches = fileDiffs.stream()
                .map(GithubFileDiffDto::patch)
                .filter(p -> p != null && !p.isBlank())
                .collect(Collectors.joining("\n"));

        if (patches.isBlank()) {
            return AiValidationResult.SKIPPED;
        }

        try {
            String apiKey = resolveApiKey();
            if (apiKey == null) return AiValidationResult.WARN;

            String promptText = DIFF_VALIDATION_PROMPT + issueDescription + "\n\nCode diff:\n" + patches;
            var request = new GeminiRequest(
                    List.of(new GeminiContent(List.of(new GeminiPart(promptText)))),
                    new GeminiRequest.GenerationConfig(10, 0.0)
            );

            return callWithRetry(request, apiKey);

        } catch (ResourceAccessException ex) {
            log.warn("LLM diff validation timed out or connection failed — returning WARN: {}", ex.getMessage());
            return AiValidationResult.WARN;
        } catch (Exception ex) {
            log.warn("Unexpected error during AI diff validation — returning WARN: {}", ex.getMessage());
            return AiValidationResult.WARN;
        }
    }

    // ── Gemini request records ────────────────────────────────────────────────

    private record GeminiPart(String text) {}

    private record GeminiContent(List<GeminiPart> parts) {}

    private record GeminiRequest(
            List<GeminiContent> contents,
            @JsonProperty("generationConfig") GenerationConfig generationConfig) {

        record GenerationConfig(
                @JsonProperty("maxOutputTokens") int maxOutputTokens,
                double temperature) {}
    }

    // ── Gemini response records ───────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiResponse(List<GeminiCandidate> candidates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiCandidate(GeminiRespContent content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiRespContent(List<GeminiRespPart> parts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiRespPart(String text) {}
}
