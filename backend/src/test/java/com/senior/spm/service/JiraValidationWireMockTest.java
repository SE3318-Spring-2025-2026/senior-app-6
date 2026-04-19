package com.senior.spm.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.senior.spm.exception.ExternalToolValidationException;
import com.senior.spm.exception.JiraValidationException;

/**
 * WireMock integration tests for JiraValidationService.
 *
 * JiraValidationService already accepts the base URL as a parameter to validate(),
 * so no production code changes are needed — tests simply pass wireMock.baseUrl().
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:test.properties")
class JiraValidationWireMockTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Autowired
    private JiraValidationService service;

    private static final String PROJECT_KEY = "SPM";
    private static final String EMAIL = "test@example.com";
    private static final String TOKEN = "test-jira-token";
    private static final String SEARCH_PATH = "/rest/api/3/project/search?keys=" + PROJECT_KEY;

    @Test
    @DisplayName("JIRA returns 200 with total=1 — validate succeeds without exception")
    void validate_success_whenJiraReturns200() {
        wireMock.stubFor(get(urlEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"total\":1}")));

        assertThatNoException().isThrownBy(
                () -> service.validate(wireMock.baseUrl(), EMAIL, PROJECT_KEY, TOKEN));
    }

    @Test
    @DisplayName("JIRA returns 200 with total=0 — throws JiraValidationException: project key not found")
    void validate_success_total0_throwsProjectNotFound() {
        wireMock.stubFor(get(urlEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"total\":0}")));

        assertThatThrownBy(() -> service.validate(wireMock.baseUrl(), EMAIL, PROJECT_KEY, TOKEN))
                .isInstanceOf(JiraValidationException.class)
                .hasMessageContaining("Project key '" + PROJECT_KEY + "' not found");
    }

    @Test
    @DisplayName("JIRA returns 401 — throws JiraValidationException: token invalid or expired")
    void validate_401_throwsInvalidToken() {
        wireMock.stubFor(get(urlEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> service.validate(wireMock.baseUrl(), EMAIL, PROJECT_KEY, TOKEN))
                .isInstanceOf(JiraValidationException.class)
                .hasMessageContaining("API token is invalid or expired");
    }

    @Test
    @DisplayName("JIRA returns 403 — throws JiraValidationException: token invalid or expired")
    void validate_403_throwsInvalidToken() {
        wireMock.stubFor(get(urlEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(403)));

        assertThatThrownBy(() -> service.validate(wireMock.baseUrl(), EMAIL, PROJECT_KEY, TOKEN))
                .isInstanceOf(JiraValidationException.class)
                .hasMessageContaining("API token is invalid or expired");
    }

    @Test
    @DisplayName("JIRA returns 404 — throws JiraValidationException: URL unreachable")
    void validate_404_throwsUnreachable() {
        wireMock.stubFor(get(urlEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> service.validate(wireMock.baseUrl(), EMAIL, PROJECT_KEY, TOKEN))
                .isInstanceOf(JiraValidationException.class)
                .hasMessageContaining("JIRA space URL is unreachable");
    }

    @Test
    @DisplayName("JIRA returns other 4xx — throws JiraValidationException: URL unreachable")
    void validate_other4xx_throwsUnreachable() {
        wireMock.stubFor(get(urlEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(400)));

        assertThatThrownBy(() -> service.validate(wireMock.baseUrl(), EMAIL, PROJECT_KEY, TOKEN))
                .isInstanceOf(JiraValidationException.class)
                .hasMessageContaining("JIRA space URL is unreachable");
    }

    @Test
    @DisplayName("JiraValidationException is an ExternalToolValidationException (422-equivalent)")
    void validate_throwsExternalToolValidationException_subtype() {
        wireMock.stubFor(get(urlEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> service.validate(wireMock.baseUrl(), EMAIL, PROJECT_KEY, TOKEN))
                .isInstanceOf(ExternalToolValidationException.class);
    }
}
