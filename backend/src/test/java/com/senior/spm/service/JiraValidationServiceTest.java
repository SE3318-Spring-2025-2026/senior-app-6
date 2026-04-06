package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
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

    private static final String JIRA_URL = "https://myteam.atlassian.net";
    private static final String PROJECT_KEY = "SPM";
    private static final String API_TOKEN = "test-api-token";
    private static final String EXPECTED_URL = JIRA_URL + "/rest/api/3/project/" + PROJECT_KEY;

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    void validate_success_whenJiraReturns200() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.OK));

        assertThatNoException().isThrownBy(() -> service.validate(JIRA_URL, PROJECT_KEY, API_TOKEN));
    }

    // ── 401 / 403 — invalid token ─────────────────────────────────────────

    @Test
    void validate_throwsInvalidToken_whenJiraReturns401() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.validate(JIRA_URL, PROJECT_KEY, API_TOKEN))
                .isInstanceOf(JiraValidationException.class)
                .hasMessage("JIRA validation failed: API token is invalid or expired");
    }

    @Test
    void validate_throwsInvalidToken_whenJiraReturns403() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.validate(JIRA_URL, PROJECT_KEY, API_TOKEN))
                .isInstanceOf(JiraValidationException.class)
                .hasMessage("JIRA validation failed: API token is invalid or expired");
    }

    // ── 404 — project key not found ──────────────────────────────────────

    @Test
    void validate_throwsProjectNotFound_whenJiraReturns404() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.validate(JIRA_URL, PROJECT_KEY, API_TOKEN))
                .isInstanceOf(JiraValidationException.class)
                .hasMessage("JIRA validation failed: Project key 'SPM' not found");
    }

    // ── Other 4xx — unreachable / misconfigured URL ──────────────────────

    @Test
    void validate_throwsUnreachable_whenJiraReturnsOther4xx() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.validate(JIRA_URL, PROJECT_KEY, API_TOKEN))
                .isInstanceOf(JiraValidationException.class)
                .hasMessage("JIRA validation failed: JIRA space URL is unreachable");
    }

    // ── Timeout / network failure ─────────────────────────────────────────

    @Test
    void validate_throwsUnreachable_whenTimeout() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        assertThatThrownBy(() -> service.validate(JIRA_URL, PROJECT_KEY, API_TOKEN))
                .isInstanceOf(JiraValidationException.class)
                .hasMessage("JIRA validation failed: JIRA space URL is unreachable");
    }

    // ── URL normalisation ─────────────────────────────────────────────────

    @Test
    void validate_handlesTrailingSlash_inJiraUrl() {
        // Trailing slash in jiraSpaceUrl must be stripped before appending the path.
        // "https://myteam.atlassian.net/" → exchange must be called with
        // "https://myteam.atlassian.net/rest/api/3/project/SPM" (single slash, not double).
        String urlWithSlash = JIRA_URL + "/";
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.OK));

        assertThatNoException().isThrownBy(() -> service.validate(urlWithSlash, PROJECT_KEY, API_TOKEN));
        verify(restTemplate).exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void validate_stripsTrailingWhitespace_fromJiraUrl() {
        String urlWithSpaces = JIRA_URL + "   ";
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.OK));

        assertThatNoException().isThrownBy(() -> service.validate(urlWithSpaces, PROJECT_KEY, API_TOKEN));
        verify(restTemplate).exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class));
    }

    // ── Exception hierarchy — GlobalExceptionHandler relies on this ───────

    @Test
    void validate_throwsExternalToolValidationException_onFailure() {
        // JiraValidationException must extend ExternalToolValidationException so that
        // GlobalExceptionHandler.handleExternalToolValidation() catches it and returns 422.
        // If this hierarchy breaks, failures silently become 500s instead of 422s.
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.validate(JIRA_URL, PROJECT_KEY, API_TOKEN))
                .isInstanceOf(ExternalToolValidationException.class);
    }

    // ── Authorization header ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void validate_setsBearerHeader() {
        when(restTemplate.exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.OK));

        service.validate(JIRA_URL, PROJECT_KEY, API_TOKEN);

        ArgumentCaptor<HttpEntity<Void>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(EXPECTED_URL), eq(HttpMethod.GET), captor.capture(), eq(Void.class));
        String authHeader = captor.getValue().getHeaders().getFirst("Authorization");
        org.assertj.core.api.Assertions.assertThat(authHeader).isEqualTo("Bearer " + API_TOKEN);
    }
}
