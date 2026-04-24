package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.service.dto.GithubFileDiffDto;
import com.senior.spm.service.dto.GithubPrDto;

/**
 * WireMock-based integration tests for {@link GithubSprintService}.
 *
 * <p>A fresh WireMock server is started before each test and stopped after,
 * guaranteeing complete stub isolation.
 *
 * <p>The service is subclassed per-test to override {@link GithubSprintService#githubApiBase()}
 * so all HTTP calls are redirected to the local WireMock server.
 *
 * <p>Issue: #150 — [Backend] GitHub Sprint Integration.
 */
class GithubSprintServiceWireMockTest {

    private WireMockServer wireMockServer;
    private GithubSprintService service;

    /**
     * Fake encryption service — returns its input unchanged so the
     * PAT used in assertions is predictable.
     */
    private static EncryptionService fakeEncryption() {
        return new EncryptionService() {
            @Override
            public String decrypt(String cipherText) {
                return cipherText;
            }
        };
    }

    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        final String baseUrl = wireMockServer.baseUrl();

        // Subclass GithubSprintService to redirect githubApiBase() → WireMock
        service = new GithubSprintService(RestClient.builder(), fakeEncryption()) {
            @Override
            protected String githubApiBase() {
                return baseUrl;
            }
        };
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: build a minimal ProjectGroup
    // ─────────────────────────────────────────────────────────────────────────

    private ProjectGroup group(String org, String repo) {
        ProjectGroup g = new ProjectGroup();
        g.setGithubOrgName(org);
        g.setGithubRepoName(repo);
        g.setEncryptedGithubPat("test-pat");
        return g;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findBranchByIssueKey
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void findBranchByIssueKey_returnsMatchingBranch() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/repos/my-org/my-repo/branches"))
                .willReturn(WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[{\"name\":\"main\"},{\"name\":\"feature/SPM-42-fix\"},{\"name\":\"feature/SPM-43-other\"}]"))
        );

        Optional<String> result = service.findBranchByIssueKey(group("my-org", "my-repo"), "SPM-42");

        assertThat(result).isPresent().hasValue("feature/SPM-42-fix");
    }

    @Test
    void findBranchByIssueKey_returnsEmpty_whenNoMatchingBranch() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/repos/my-org/my-repo/branches"))
                .willReturn(WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[{\"name\":\"main\"},{\"name\":\"feature/OTHER-99-something\"}]"))
        );

        assertThatCode(() -> {
            Optional<String> result = service.findBranchByIssueKey(group("my-org", "my-repo"), "SPM-42");
            assertThat(result).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    void findBranchByIssueKey_returnsEmpty_on404() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/repos/my-org/my-repo/branches"))
                .willReturn(WireMock.aResponse().withStatus(404))
        );

        assertThatCode(() -> {
            Optional<String> result = service.findBranchByIssueKey(group("my-org", "my-repo"), "SPM-42");
            assertThat(result).isEmpty();
        }).doesNotThrowAnyException();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findMergedPR
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void findMergedPR_returnsMergedTrue_whenClosedAndMergedAtPresent() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/repos/my-org/my-repo/pulls"))
                .willReturn(WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[{\"number\":7,\"state\":\"closed\",\"merged_at\":\"2026-04-20T10:00:00Z\"}]"))
        );

        Optional<GithubPrDto> result = service.findMergedPR(group("my-org", "my-repo"), "feature/SPM-42-fix");

        assertThat(result).isPresent();
        assertThat(result.get().prNumber()).isEqualTo(7L);
        assertThat(result.get().merged()).isTrue();
    }

    @Test
    void findMergedPR_returnsMergedFalse_whenClosedButNoMergedAt() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/repos/my-org/my-repo/pulls"))
                .willReturn(WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[{\"number\":8,\"state\":\"closed\",\"merged_at\":null}]"))
        );

        Optional<GithubPrDto> result = service.findMergedPR(group("my-org", "my-repo"), "feature/SPM-42-fix");

        assertThat(result).isPresent();
        assertThat(result.get().merged()).isFalse();
    }

    @Test
    void findMergedPR_returnsEmpty_whenNoPRFound() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/repos/my-org/my-repo/pulls"))
                .willReturn(WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[]"))
        );

        assertThatCode(() -> {
            Optional<GithubPrDto> result = service.findMergedPR(group("my-org", "my-repo"), "feature/SPM-99-gone");
            assertThat(result).isEmpty();
        }).doesNotThrowAnyException();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // fetchPRReviewComments
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void fetchPRReviewComments_returnsBodies() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlEqualTo("/repos/my-org/my-repo/pulls/7/reviews"))
                .willReturn(WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[{\"state\":\"APPROVED\",\"body\":\"LGTM\"},{\"state\":\"COMMENTED\",\"body\":\"Please add tests\"},{\"state\":\"APPROVED\",\"body\":\"\"}]"))
        );

        List<String> comments = service.fetchPRReviewComments(group("my-org", "my-repo"), 7L);

        assertThat(comments).containsExactly("LGTM", "Please add tests");
    }

    @Test
    void fetchPRReviewComments_returnsEmpty_on401() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlEqualTo("/repos/my-org/my-repo/pulls/7/reviews"))
                .willReturn(WireMock.aResponse().withStatus(401))
        );

        assertThatCode(() -> {
            List<String> result = service.fetchPRReviewComments(group("my-org", "my-repo"), 7L);
            assertThat(result).isEmpty();
        }).doesNotThrowAnyException();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // fetchFileDiffs
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void fetchFileDiffs_includesPatchString() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlEqualTo("/repos/my-org/my-repo/pulls/7/files"))
                .willReturn(WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[{\"filename\":\"src/main/Foo.java\",\"patch\":\"@@ -1,4 +1,6 @@\\n+import java.util.List;\"},{\"filename\":\"README.md\",\"patch\":\"@@ -10,3 +10,5 @@\\n+## New section\"}]"))
        );

        List<GithubFileDiffDto> diffs = service.fetchFileDiffs(group("my-org", "my-repo"), 7L);

        assertThat(diffs).hasSize(2);

        GithubFileDiffDto first = diffs.get(0);
        assertThat(first.filename()).isEqualTo("src/main/Foo.java");
        assertThat(first.patch()).contains("import java.util.List;");

        GithubFileDiffDto second = diffs.get(1);
        assertThat(second.filename()).isEqualTo("README.md");
        assertThat(second.patch()).contains("New section");
    }

    @Test
    void fetchFileDiffs_returnsEmpty_on500() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlEqualTo("/repos/my-org/my-repo/pulls/7/files"))
                .willReturn(WireMock.aResponse().withStatus(500))
        );

        assertThatCode(() -> {
            List<GithubFileDiffDto> result = service.fetchFileDiffs(group("my-org", "my-repo"), 7L);
            assertThat(result).isEmpty();
        }).doesNotThrowAnyException();
    }
}
