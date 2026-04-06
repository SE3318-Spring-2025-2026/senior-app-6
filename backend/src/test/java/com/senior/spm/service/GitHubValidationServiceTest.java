package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import com.senior.spm.exception.GitHubValidationException;

@ExtendWith(MockitoExtension.class)
class GitHubValidationServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private GitHubValidationService service;

    private static final String ORG_NAME = "my-org";
    private static final String PAT = "ghp_testtoken";
    private static final String STEP1_URL = "https://api.github.com/orgs/" + ORG_NAME;
    private static final String STEP2_URL = "https://api.github.com/orgs/" + ORG_NAME + "/repos?per_page=1";

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    void validate_success_whenBothCallsReturn200() {
        when(restTemplate.exchange(eq(STEP1_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.OK));
        when(restTemplate.exchange(eq(STEP2_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.OK));

        assertThatNoException().isThrownBy(() -> service.validate(ORG_NAME, PAT));

        verify(restTemplate).exchange(eq(STEP1_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class));
        verify(restTemplate).exchange(eq(STEP2_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class));
    }

    // ── Step 1 failures — fail-fast: Step 2 must never be called ─────────

    @Test
    void validate_throwsInvalidPat_whenStep1Returns401() {
        when(restTemplate.exchange(eq(STEP1_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.validate(ORG_NAME, PAT))
                .isInstanceOf(GitHubValidationException.class)
                .hasMessage("GitHub validation failed: PAT is invalid or expired");

        verify(restTemplate, never()).exchange(eq(STEP2_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void validate_throwsOrgNotFound_whenStep1Returns404() {
        when(restTemplate.exchange(eq(STEP1_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.validate(ORG_NAME, PAT))
                .isInstanceOf(GitHubValidationException.class)
                .hasMessage("GitHub validation failed: Organization 'my-org' not found");

        verify(restTemplate, never()).exchange(eq(STEP2_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void validate_throwsInvalidPat_whenStep1ReturnsOther4xx() {
        when(restTemplate.exchange(eq(STEP1_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.validate(ORG_NAME, PAT))
                .isInstanceOf(GitHubValidationException.class)
                .hasMessage("GitHub validation failed: PAT is invalid or expired");

        verify(restTemplate, never()).exchange(eq(STEP2_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void validate_throwsInvalidPat_whenStep1TimesOut() {
        when(restTemplate.exchange(eq(STEP1_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        assertThatThrownBy(() -> service.validate(ORG_NAME, PAT))
                .isInstanceOf(GitHubValidationException.class)
                .hasMessage("GitHub validation failed: PAT is invalid or expired");

        verify(restTemplate, never()).exchange(eq(STEP2_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class));
    }

    // ── Step 2 failures (Step 1 always succeeds) ─────────────────────────

    @Test
    void validate_throwsLacksRepoScope_whenStep2Returns403() {
        when(restTemplate.exchange(eq(STEP1_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.OK));
        when(restTemplate.exchange(eq(STEP2_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.validate(ORG_NAME, PAT))
                .isInstanceOf(GitHubValidationException.class)
                .hasMessage("GitHub validation failed: PAT lacks required 'repo' scope");
    }

    @Test
    void validate_throwsLacksRepoScope_whenStep2ReturnsOther4xx() {
        when(restTemplate.exchange(eq(STEP1_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.OK));
        when(restTemplate.exchange(eq(STEP2_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.validate(ORG_NAME, PAT))
                .isInstanceOf(GitHubValidationException.class)
                .hasMessage("GitHub validation failed: PAT lacks required 'repo' scope");
    }

    @Test
    void validate_throwsLacksRepoScope_whenStep2TimesOut() {
        when(restTemplate.exchange(eq(STEP1_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.OK));
        when(restTemplate.exchange(eq(STEP2_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        assertThatThrownBy(() -> service.validate(ORG_NAME, PAT))
                .isInstanceOf(GitHubValidationException.class)
                .hasMessage("GitHub validation failed: PAT lacks required 'repo' scope");
    }

    // ── Exception hierarchy — GlobalExceptionHandler relies on this ───────

    @Test
    void validate_throwsExternalToolValidationException_onFailure() {
        // GitHubValidationException must extend ExternalToolValidationException so that
        // GlobalExceptionHandler.handleExternalToolValidation() catches it and returns 422.
        when(restTemplate.exchange(eq(STEP1_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.validate(ORG_NAME, PAT))
                .isInstanceOf(ExternalToolValidationException.class);
    }

    // ── Authorization header ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void validate_setsBearerHeader() {
        when(restTemplate.exchange(eq(STEP1_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.OK));
        when(restTemplate.exchange(eq(STEP2_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.OK));

        service.validate(ORG_NAME, PAT);

        ArgumentCaptor<HttpEntity<Void>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(STEP1_URL), eq(HttpMethod.GET), captor.capture(), eq(Void.class));
        String authHeader = captor.getValue().getHeaders().getFirst("Authorization");
        org.assertj.core.api.Assertions.assertThat(authHeader).isEqualTo("Bearer " + PAT);
    }
}
