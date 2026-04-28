package com.senior.spm;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.entity.ProjectGroup.GroupStatus;
import com.senior.spm.entity.Sprint;
import com.senior.spm.entity.SprintTrackingLog;
import com.senior.spm.entity.SprintTrackingLog.AiValidationResult;
import com.senior.spm.entity.SystemConfig;
import com.senior.spm.repository.ProjectGroupRepository;
import com.senior.spm.repository.SprintDeliverableMappingRepository;
import com.senior.spm.repository.SprintRepository;
import com.senior.spm.repository.SprintTrackingLogRepository;
import com.senior.spm.repository.SystemConfigRepository;
import com.senior.spm.service.AiValidationService;
import com.senior.spm.service.EncryptionService;
import com.senior.spm.service.GithubSprintService;
import com.senior.spm.service.SprintTrackingOrchestrator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for the sprint tracking pipeline (5.1–5.4).
 *
 * Uses WireMock to mock JIRA, GitHub, and LLM APIs and verifies the
 * SprintTrackingLog state after orchestrator execution.
 *
 * Covered scenarios:
 * - happy path
 * - JIRA 401 fault isolation
 * - missing GitHub branch
 * - unmerged PR
 * - LLM timeout
 * - manual refresh idempotency
 *
 * Tests call triggerForSprint() directly and do not depend on scheduler timing.
 *
 * Database cleanup is handled in @BeforeEach because inner REQUIRES_NEW
 * transactions are committed independently.
 */


@SpringBootTest
@TestPropertySource(locations = "classpath:test.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SprintTrackingOrchestratorE2ETest {

    // ── WireMock servers ─────────────────────────────────────────────────────
    // Started in a static initializer so they are available to @DynamicPropertySource.

    static final WireMockServer jiraWireMock;
    static final WireMockServer githubWireMock;
    static final WireMockServer llmWireMock;

    static {
        jiraWireMock   = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        githubWireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        llmWireMock    = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        jiraWireMock.start();
        githubWireMock.start();
        llmWireMock.start();
    }

    /** Point AiValidationService at the LLM WireMock before the Spring context starts. */
    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry reg) {
        reg.add("llm.api.base-url", llmWireMock::baseUrl);
    }

    @AfterAll
    static void stopServers() {
        jiraWireMock.stop();
        githubWireMock.stop();
        llmWireMock.stop();
    }

    // ── Override GithubSprintService so githubApiBase() points at WireMock ───

    @TestConfiguration
    static class GitHubTestConfig {
        @Bean("testGithubSprintService")
        @Primary
        GithubSprintService testGithubSprintService(RestClient.Builder builder, EncryptionService enc) {
            return new GithubSprintService(builder, enc) {
                @Override
                protected String githubApiBase() {
                    return githubWireMock.baseUrl();
                }
            };
        }
    }

    // ── Autowired ──────────────────────────────────────────────────────────────

    @Autowired SprintTrackingOrchestrator          orchestrator;
    @Autowired SprintTrackingLogRepository         sprintTrackingLogRepository;
    @Autowired SprintDeliverableMappingRepository  sprintDeliverableMappingRepository;
    @Autowired SprintRepository                    sprintRepository;
    @Autowired ProjectGroupRepository              projectGroupRepository;
    @Autowired SystemConfigRepository              systemConfigRepository;
    @Autowired EncryptionService                   encryptionService;
    @Autowired AiValidationService                 aiValidationService;

    private static final String TERM_ID = "2026-SPRING-P5-TEST";

    // ── Per-test setup ────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        jiraWireMock.resetAll();
        githubWireMock.resetAll();
        llmWireMock.resetAll();

        // FK-safe teardown order:
        //   SprintTrackingLog  → Sprint, ProjectGroup
        //   SprintDeliverableMapping → Sprint
        //   ProjectGroup (no child rows in these tests)
        //   Sprint
        // Note: @Transactional rollback is intentionally NOT used here.
        // processGroup() runs with REQUIRES_NEW, which commits independently of any outer
        // test transaction — rollback on the test method would not undo those rows.
        sprintTrackingLogRepository.deleteAll();
        sprintDeliverableMappingRepository.deleteAll();
        projectGroupRepository.deleteAll();
        sprintRepository.deleteAll();
        systemConfigRepository.deleteAll();

        systemConfigRepository.save(termConfig());

        // Inject the LLM key directly into the cache, bypassing DB encryption.
        // All tests that exercise AI validation use "fake-llm-key" in WireMock stubs.
        ReflectionTestUtils.setField(aiValidationService, "cachedApiKey",
                new AtomicReference<>("fake-llm-key"));
    }

    // =========================================================================
    // Test 1 — Happy path: sprint ended → rows created for all eligible groups
    // =========================================================================

    @Test
    void happyPath_endedSprint_createsTrackingRowsForAllEligibleGroups() {
        Sprint sprint = sprintRepository.save(endedSprint());
        projectGroupRepository.save(newGroup("G1", GroupStatus.TOOLS_BOUND,     "KEY-G1", "org1", "repo1"));
        projectGroupRepository.save(newGroup("G2", GroupStatus.ADVISOR_ASSIGNED,"KEY-G2", "org2", "repo2"));

        stubJiraHappyPath("KEY-G1", 1, "SPM-10", 3);   // board 100, sprint 101
        stubJiraHappyPath("KEY-G2", 2, "SPM-20", 5);   // board 200, sprint 201

        stubGithubBranch("org1", "repo1", "SPM-10-impl");
        stubGithubMergedPr("org1", "repo1", "org1:SPM-10-impl", 11L);
        stubGithubReviews("org1", "repo1", 11L, "Looks good");
        stubGithubDiffs("org1", "repo1", 11L);

        stubGithubBranch("org2", "repo2", "SPM-20-impl");
        stubGithubMergedPr("org2", "repo2", "org2:SPM-20-impl", 21L);
        stubGithubReviews("org2", "repo2", 21L, "Well done");
        stubGithubDiffs("org2", "repo2", 21L);

        stubLlmPass();

        var stats = orchestrator.triggerForSprint(sprint.getId(), false);

        assertThat(stats.groupsProcessed()).isEqualTo(2);
        assertThat(stats.issuesFetched()).isEqualTo(2);

        List<SprintTrackingLog> logs = sprintTrackingLogRepository.findBySprintId(sprint.getId());
        assertThat(logs).hasSize(2);
        assertThat(logs).allMatch(l -> Boolean.TRUE.equals(l.getPrMerged()));
        assertThat(logs).allMatch(l -> l.getAiPrResult() == AiValidationResult.PASS);
    }

    // =========================================================================
    // Test 2 — JIRA 401: failing group skipped; other group rows present
    // =========================================================================

    @Test
    void jira401_failingGroupSkipped_otherGroupHasRows() {
        Sprint sprint     = sprintRepository.save(endedSprint());
        ProjectGroup fail = projectGroupRepository.save(newGroup("Fail-G", GroupStatus.TOOLS_BOUND, "KEY-FAIL", "orgF", "repoF"));
        ProjectGroup ok   = projectGroupRepository.save(newGroup("Ok-G",   GroupStatus.TOOLS_BOUND, "KEY-OK",   "orgOk","repoOk"));

        // Stub the JIRA board lookup for the failing group → 401
        jiraWireMock.stubFor(get(urlPathEqualTo("/rest/agile/1.0/board"))
                .withQueryParam("projectKeyOrId", equalTo("KEY-FAIL"))
                .willReturn(aResponse().withStatus(401)));

        stubJiraHappyPath("KEY-OK", 3, "SPM-30", 2);   // board 300, sprint 301

        stubGithubBranch("orgOk", "repoOk", "SPM-30-impl");
        stubGithubMergedPr("orgOk", "repoOk", "orgOk:SPM-30-impl", 31L);
        stubGithubReviews("orgOk", "repoOk", 31L, "Fine");
        stubGithubDiffs("orgOk", "repoOk", 31L);
        stubLlmPass();

        orchestrator.triggerForSprint(sprint.getId(), false);

        long failRows = sprintTrackingLogRepository
                .findByGroupIdAndSprintId(fail.getId(), sprint.getId()).size();
        long okRows   = sprintTrackingLogRepository
                .findByGroupIdAndSprintId(ok.getId(), sprint.getId()).size();

        assertThat(failRows).as("failing group must have zero rows").isZero();
        assertThat(okRows).as("ok group must have rows").isEqualTo(1);
    }

    // =========================================================================
    // Test 3 — No matching GitHub branch → prMerged=null, AI=SKIPPED
    // =========================================================================

    @Test
    void noMatchingGithubBranch_prMergedNull_aiResultsSkipped() {
        Sprint sprint = sprintRepository.save(endedSprint());
        projectGroupRepository.save(newGroup("G-NoBranch", GroupStatus.TOOLS_BOUND, "KEY-NB", "org-nb", "repo-nb"));

        stubJiraHappyPath("KEY-NB", 4, "SPM-40", 3);   // board 400, sprint 401

        // Empty branch list — no branch matches the issue key
        githubWireMock.stubFor(get(urlPathEqualTo("/repos/org-nb/repo-nb/branches"))
                .willReturn(okJson("[]")));

        orchestrator.triggerForSprint(sprint.getId(), false);

        List<SprintTrackingLog> logs = sprintTrackingLogRepository.findBySprintId(sprint.getId());
        assertThat(logs).hasSize(1);
        SprintTrackingLog log = logs.get(0);
        assertThat(log.getPrMerged()).isNull();
        assertThat(log.getAiPrResult()).isEqualTo(AiValidationResult.SKIPPED);
        assertThat(log.getAiDiffResult()).isEqualTo(AiValidationResult.SKIPPED);
    }

    // =========================================================================
    // Test 4 — PR found but not merged → prMerged=false, AI=SKIPPED
    // =========================================================================

    @Test
    void prFoundButNotMerged_prMergedFalse_aiResultsSkipped() {
        Sprint sprint = sprintRepository.save(endedSprint());
        projectGroupRepository.save(newGroup("G-UnmergedPR", GroupStatus.TOOLS_BOUND, "KEY-UM", "org-um", "repo-um"));

        stubJiraHappyPath("KEY-UM", 5, "SPM-50", 2);   // board 500, sprint 501

        stubGithubBranch("org-um", "repo-um", "SPM-50-impl");

        // Closed PR with merged_at=null → not merged
        githubWireMock.stubFor(get(urlPathEqualTo("/repos/org-um/repo-um/pulls"))
                .withQueryParam("head", equalTo("org-um:SPM-50-impl"))
                .willReturn(okJson("""
                        [{"number":99,"state":"closed","merged_at":null,"user":{"login":"dev"}}]
                        """)));

        orchestrator.triggerForSprint(sprint.getId(), false);

        List<SprintTrackingLog> logs = sprintTrackingLogRepository.findBySprintId(sprint.getId());
        assertThat(logs).hasSize(1);
        SprintTrackingLog log = logs.get(0);
        assertThat(log.getPrMerged()).isFalse();
        assertThat(log.getAiPrResult()).isEqualTo(AiValidationResult.SKIPPED);
        assertThat(log.getAiDiffResult()).isEqualTo(AiValidationResult.SKIPPED);
    }

    // =========================================================================
    // Test 5 — LLM timeout → aiPrResult=WARN, orchestrator proceeds normally
    // =========================================================================

    @Test
    void llmTimeout_aiPrResultWarn_orchestratorCompletes() {
        Sprint sprint = sprintRepository.save(endedSprint());
        projectGroupRepository.save(newGroup("G-LLMTimeout", GroupStatus.TOOLS_BOUND, "KEY-LT", "org-lt", "repo-lt"));

        stubJiraHappyPath("KEY-LT", 6, "SPM-60", 3);   // board 600, sprint 601

        stubGithubBranch("org-lt", "repo-lt", "SPM-60-impl");
        stubGithubMergedPr("org-lt", "repo-lt", "org-lt:SPM-60-impl", 61L);
        stubGithubReviews("org-lt", "repo-lt", 61L, "Comment");
        stubGithubDiffs("org-lt", "repo-lt", 61L);

        // Delay 2 s — test timeout is 1 s (llm.api.timeout-seconds=1 in test.properties)
        // → ResourceAccessException → validatePRReview returns WARN
        llmWireMock.stubFor(post(urlPathEqualTo("/v1beta/models/gemini-2.5-flash-lite:generateContent"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(llmPassBody())
                        .withFixedDelay(2_000)));

        orchestrator.triggerForSprint(sprint.getId(), false);

        List<SprintTrackingLog> logs = sprintTrackingLogRepository.findBySprintId(sprint.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAiPrResult()).isEqualTo(AiValidationResult.WARN);
    }

    // =========================================================================
    // Test 6 — Idempotency: second triggerForSprint row count == first
    // =========================================================================

    @Test
    void idempotency_secondTrigger_rowCountEqualsFirst() {
        Sprint sprint = sprintRepository.save(endedSprint());
        projectGroupRepository.save(newGroup("G-Idem", GroupStatus.TOOLS_BOUND, "KEY-ID", "org-id", "repo-id"));

        stubJiraHappyPath("KEY-ID", 7, "SPM-70", 3);   // board 700, sprint 701

        stubGithubBranch("org-id", "repo-id", "SPM-70-impl");
        stubGithubMergedPr("org-id", "repo-id", "org-id:SPM-70-impl", 71L);
        stubGithubReviews("org-id", "repo-id", 71L, "Approved");
        stubGithubDiffs("org-id", "repo-id", 71L);
        stubLlmPass();

        orchestrator.triggerForSprint(sprint.getId(), true);
        long countAfterFirst = sprintTrackingLogRepository.findBySprintId(sprint.getId()).size();

        orchestrator.triggerForSprint(sprint.getId(), true);
        long countAfterSecond = sprintTrackingLogRepository.findBySprintId(sprint.getId()).size();

        assertThat(countAfterFirst).isEqualTo(1);
        assertThat(countAfterSecond).as("idempotent: second run must not grow the row set").isEqualTo(countAfterFirst);
    }

    // =========================================================================
    // WireMock stub helpers
    // =========================================================================

    /**
     * Stubs the 3-step JIRA resolution chain (board → sprint → issues) for one group.
     * {@code seed} makes board/sprint IDs unique per group to avoid stub collisions
     * when multiple groups share the same JIRA WireMock server.
     */
    private void stubJiraHappyPath(String projectKey, int seed, String issueKey, int storyPoints) {
        int boardId   = seed * 100;
        int sprintId  = seed * 100 + 1;

        jiraWireMock.stubFor(get(urlPathEqualTo("/rest/agile/1.0/board"))
                .withQueryParam("projectKeyOrId", equalTo(projectKey))
                .willReturn(okJson(String.format(
                        "{\"values\":[{\"id\":%d}]}", boardId))));

        jiraWireMock.stubFor(get(urlPathEqualTo("/rest/agile/1.0/board/" + boardId + "/sprint"))
                .withQueryParam("state", equalTo("active"))
                .willReturn(okJson(String.format(
                        "{\"values\":[{\"id\":\"%d\"}]}", sprintId))));

        jiraWireMock.stubFor(get(urlPathEqualTo("/rest/agile/1.0/sprint/" + sprintId + "/issue"))
                .willReturn(okJson(String.format(
                        "{\"maxResults\":50,\"startAt\":0,\"total\":1,\"issues\":[" +
                        "{\"key\":\"%s\",\"fields\":{\"customfield_10016\":%d,\"description\":\"desc\"}}]}",
                        issueKey, storyPoints))));
    }

    private void stubGithubBranch(String org, String repo, String branchName) {
        githubWireMock.stubFor(get(urlPathEqualTo("/repos/" + org + "/" + repo + "/branches"))
                .willReturn(okJson(String.format("[{\"name\":\"%s\"}]", branchName))));
    }

    private void stubGithubMergedPr(String org, String repo, String head, long prNumber) {
        githubWireMock.stubFor(get(urlPathEqualTo("/repos/" + org + "/" + repo + "/pulls"))
                .withQueryParam("head", equalTo(head))
                .willReturn(okJson(String.format(
                        "[{\"number\":%d,\"state\":\"closed\",\"merged_at\":\"2025-01-01T12:00:00Z\"," +
                        "\"user\":{\"login\":\"dev\"}}]", prNumber))));
    }

    private void stubGithubReviews(String org, String repo, long prNumber, String body) {
        // Formal reviews endpoint — called first by fetchPRReviewComments
        githubWireMock.stubFor(get(urlPathEqualTo(
                "/repos/" + org + "/" + repo + "/pulls/" + prNumber + "/reviews"))
                .willReturn(okJson(String.format("[{\"state\":\"APPROVED\",\"body\":\"%s\"}]", body))));
        // Inline diff comments endpoint
        githubWireMock.stubFor(get(urlPathEqualTo(
                "/repos/" + org + "/" + repo + "/pulls/" + prNumber + "/comments"))
                .willReturn(okJson("[]")));
    }

    private void stubGithubDiffs(String org, String repo, long prNumber) {
        githubWireMock.stubFor(get(urlPathEqualTo(
                "/repos/" + org + "/" + repo + "/pulls/" + prNumber + "/files"))
                .willReturn(okJson("[{\"filename\":\"Foo.java\",\"patch\":\"+code\"}]")));
    }

    private void stubLlmPass() {
        llmWireMock.stubFor(post(urlPathEqualTo("/v1beta/models/gemini-2.5-flash-lite:generateContent"))
                .willReturn(okJson(llmPassBody())));
    }

    private static String llmPassBody() {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"PASS\"}]}}]}";
    }

    // =========================================================================
    // Entity helpers
    // =========================================================================

    private Sprint endedSprint() {
        Sprint s = new Sprint();
        s.setStartDate(LocalDate.now(ZoneId.of("UTC")).minusDays(8));
        s.setEndDate(LocalDate.now(ZoneId.of("UTC")).minusDays(1));
        return s;
    }

    private ProjectGroup newGroup(String name, GroupStatus status,
                                   String projectKey, String org, String repo) {
        ProjectGroup g = new ProjectGroup();
        g.setGroupName(name);
        g.setTermId(TERM_ID);
        g.setStatus(status);
        g.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));
        g.setJiraSpaceUrl(jiraWireMock.baseUrl());
        g.setJiraEmail("test@example.com");
        g.setJiraProjectKey(projectKey);
        g.setEncryptedJiraToken(encryptionService.encrypt("fake-jira-token"));
        g.setGithubOrgName(org);
        g.setGithubRepoName(repo);
        g.setEncryptedGithubPat(encryptionService.encrypt("fake-github-pat"));
        return g;
    }

    private SystemConfig termConfig() {
        SystemConfig c = new SystemConfig();
        c.setConfigKey("active_term_id");
        c.setConfigValue(TERM_ID);
        return c;
    }
}
