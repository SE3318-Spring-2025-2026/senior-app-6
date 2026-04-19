package com.senior.spm.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.senior.spm.exception.ExternalToolValidationException;
import com.senior.spm.exception.GitHubValidationException;

/**
 * WireMock integration tests for GitHubValidationService.
 *
 * Uses a real RestTemplate (with the 5s timeout from RestTemplateConfig)
 * pointed at a local WireMock HTTP server. The service base URL is overridden
 * via constructor so no production code hits real GitHub.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:test.properties")
class GitHubValidationWireMockTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Autowired
    private RestTemplate restTemplate;

    private GitHubValidationService service;

    private static final String ORG = "test-org";
    private static final String PAT = "ghp_test_token";

    @BeforeEach
    void setUp() {
        service = new GitHubValidationService(restTemplate, wireMock.baseUrl());
    }

    @Test
    @DisplayName("Both calls return 200 — validate succeeds without exception")
    void validate_success_whenBothCallsReturn200() {
        wireMock.stubFor(get(urlEqualTo("/orgs/" + ORG))
                .willReturn(aResponse().withStatus(200)));
        wireMock.stubFor(get(urlEqualTo("/orgs/" + ORG + "/repos?per_page=1"))
                .willReturn(aResponse().withStatus(200)));

        assertThatNoException().isThrownBy(() -> service.validate(ORG, PAT));
    }

    @Test
    @DisplayName("Step 1 returns 401 — throws GitHubValidationException: PAT invalid or expired")
    void validate_401_throwsInvalidPat() {
        wireMock.stubFor(get(urlEqualTo("/orgs/" + ORG))
                .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> service.validate(ORG, PAT))
                .isInstanceOf(GitHubValidationException.class)
                .hasMessageContaining("PAT is invalid or expired");
    }

    @Test
    @DisplayName("Step 1 returns 404 — throws GitHubValidationException: org not found")
    void validate_404_throwsOrgNotFound() {
        wireMock.stubFor(get(urlEqualTo("/orgs/" + ORG))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> service.validate(ORG, PAT))
                .isInstanceOf(GitHubValidationException.class)
                .hasMessageContaining("Organization '" + ORG + "' not found");
    }

    @Test
    @DisplayName("Step 1 passes (200), Step 2 returns 403 — throws GitHubValidationException: lacks repo scope")
    void validate_step2_403_throwsLacksRepoScope() {
        wireMock.stubFor(get(urlEqualTo("/orgs/" + ORG))
                .willReturn(aResponse().withStatus(200)));
        wireMock.stubFor(get(urlEqualTo("/orgs/" + ORG + "/repos?per_page=1"))
                .willReturn(aResponse().withStatus(403)));

        assertThatThrownBy(() -> service.validate(ORG, PAT))
                .isInstanceOf(GitHubValidationException.class)
                .hasMessageContaining("PAT lacks required 'repo' scope");
    }

    @Test
    @DisplayName("GitHubValidationException is an ExternalToolValidationException (422-equivalent)")
    void validate_throwsExternalToolValidationException_subtype() {
        wireMock.stubFor(get(urlEqualTo("/orgs/" + ORG))
                .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> service.validate(ORG, PAT))
                .isInstanceOf(ExternalToolValidationException.class);
    }
}
