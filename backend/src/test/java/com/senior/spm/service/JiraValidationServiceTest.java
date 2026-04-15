package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.senior.spm.exception.ExternalToolValidationException;
import com.senior.spm.exception.JiraValidationException;

@ExtendWith(MockitoExtension.class)
class JiraValidationServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private JiraValidationService service;

    private static final String JIRA_URL   = "https://myteam.atlassian.net";
    private static final String JIRA_EMAIL = "user@example.com";
    private static final String PROJECT_KEY = "SPM";
    private static final String API_TOKEN  = "test-api-token";

    // Service uses /rest/api/3/project/search?keys={KEY} (uppercased)
    private static final String EXPECTED_URL = JIRA_URL + "/rest/api/3/project/search?keys=" + PROJECT_KEY;

    /** Helper: build the ResponseEntity the search endpoint returns (raw Map matches Map.class token). */
    @SuppressWarnings("rawtypes")
    private static ResponseEntity<Map> searchResponse(int total) {
        return ResponseEntity.ok(Map.of("total", total, "values", List.of()));
    }

    // ── Happy path — Basic Auth (email provided) ──────────────────────────────

    @Test
    void validate_success_whenSearchReturnsProjects_withEmail() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(searchResponse(1));

        assertThatNoException().isThrownBy(
                () -> service.validate(JIRA_URL, JIRA_EMAIL, PROJECT_KEY, API_TOKEN));
    }

    // ── Happy path — Bearer fallback (null email) ─────────────────────────────

    @Test
    void validate_success_whenNullEmail_fallsBackToBearer() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(searchResponse(1));

        assertThatNoException().isThrownBy(
                () -> service.validate(JIRA_URL, null, PROJECT_KEY, API_TOKEN));
    }

    // ── Project not found — search returns total: 0 ───────────────────────────

    @Test
    void validate_throwsProjectNotFound_whenSearchReturnsTotal0() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(searchResponse(0));

        assertThatThrownBy(() -> service.validate(JIRA_URL, JIRA_EMAIL, PROJECT_KEY, API_TOKEN))
                .isInstanceOf(JiraValidationException.class)
                .hasMessageContaining("Project key 'SPM' not found");
    }

    // ── 401 / 403 — invalid token ─────────────────────────────────────────────

    @Test
    void validate_throwsInvalidToken_whenJiraReturns401() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.validate(JIRA_URL, JIRA_EMAIL, PROJECT_KEY, API_TOKEN))
                .isInstanceOf(JiraValidationException.class)
                .hasMessage("JIRA validation failed: API token is invalid or expired");
    }

    @Test
    void validate_throwsInvalidToken_whenJiraReturns403() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.validate(JIRA_URL, JIRA_EMAIL, PROJECT_KEY, API_TOKEN))
                .isInstanceOf(JiraValidationException.class)
                .hasMessage("JIRA validation failed: API token is invalid or expired");
    }

    // ── Other 4xx — unreachable / misconfigured URL ───────────────────────────

    @Test
    void validate_throwsUnreachable_whenJiraReturnsOther4xx() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.validate(JIRA_URL, JIRA_EMAIL, PROJECT_KEY, API_TOKEN))
                .isInstanceOf(JiraValidationException.class)
                .hasMessage("JIRA validation failed: JIRA space URL is unreachable");
    }

    // ── Timeout / network failure ─────────────────────────────────────────────

    @Test
    void validate_throwsUnreachable_whenNetworkTimeout() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        assertThatThrownBy(() -> service.validate(JIRA_URL, JIRA_EMAIL, PROJECT_KEY, API_TOKEN))
                .isInstanceOf(JiraValidationException.class)
                .hasMessage("JIRA validation failed: JIRA space URL is unreachable");
    }

    // ── URL normalisation ─────────────────────────────────────────────────────

    @Test
    void validate_stripsTrailingSlash_fromJiraUrl() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(searchResponse(1));

        assertThatNoException().isThrownBy(
                () -> service.validate(JIRA_URL + "/", JIRA_EMAIL, PROJECT_KEY, API_TOKEN));
        verify(restTemplate).exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void validate_stripsTrailingWhitespace_fromJiraUrl() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(searchResponse(1));

        assertThatNoException().isThrownBy(
                () -> service.validate(JIRA_URL + "   ", JIRA_EMAIL, PROJECT_KEY, API_TOKEN));
        verify(restTemplate).exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    }

    // ── Project key normalisation ─────────────────────────────────────────────

    @Test
    void validate_uppercasesProjectKey_beforeBuildingUrl() {
        String expectedUrl = JIRA_URL + "/rest/api/3/project/search?keys=SPM";
        when(restTemplate.exchange(eq(expectedUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(searchResponse(1));

        assertThatNoException().isThrownBy(
                () -> service.validate(JIRA_URL, JIRA_EMAIL, "spm", API_TOKEN));
        verify(restTemplate).exchange(eq(expectedUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    }

    // ── Authorization header — Basic Auth ─────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void validate_setsBasicAuthHeader_whenEmailProvided() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(searchResponse(1));

        service.validate(JIRA_URL, JIRA_EMAIL, PROJECT_KEY, API_TOKEN);

        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), captor.capture(), eq(Map.class));

        String expectedBase64 = Base64.getEncoder()
                .encodeToString((JIRA_EMAIL + ":" + API_TOKEN).getBytes(StandardCharsets.UTF_8));
        assertThat(captor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Basic " + expectedBase64);
    }

    // ── Authorization header — Bearer fallback ────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void validate_setsBearerHeader_whenEmailIsNull() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(searchResponse(1));

        service.validate(JIRA_URL, null, PROJECT_KEY, API_TOKEN);

        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), captor.capture(), eq(Map.class));

        assertThat(captor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer " + API_TOKEN);
    }

    @SuppressWarnings("unchecked")
    @Test
    void validate_setsBearerHeader_whenEmailIsBlank() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(searchResponse(1));

        service.validate(JIRA_URL, "   ", PROJECT_KEY, API_TOKEN);

        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), captor.capture(), eq(Map.class));

        assertThat(captor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer " + API_TOKEN);
    }

    // ── Accept header ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void validate_setsAcceptJsonHeader() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(searchResponse(1));

        service.validate(JIRA_URL, JIRA_EMAIL, PROJECT_KEY, API_TOKEN);

        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), captor.capture(), eq(Map.class));

        assertThat(captor.getValue().getHeaders().getFirst(HttpHeaders.ACCEPT))
                .contains("application/json");
    }

    // ── Exception hierarchy — GlobalExceptionHandler maps this to 422 ─────────

    @Test
    void validate_throwsExternalToolValidationException_onAnyFailure() {
        // JiraValidationException must extend ExternalToolValidationException so that
        // GlobalExceptionHandler.handleExternalToolValidation() catches it and maps to 422.
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.validate(JIRA_URL, JIRA_EMAIL, PROJECT_KEY, API_TOKEN))
                .isInstanceOf(ExternalToolValidationException.class);
    }
}
