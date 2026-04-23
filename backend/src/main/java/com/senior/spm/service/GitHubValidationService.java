package com.senior.spm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.senior.spm.exception.GitHubValidationException;

/**
 * Validates GitHub credentials by making two sequential live HTTP calls to the
 * GitHub REST API.
 *
 * Called by GroupService.bindGitHub()
 * Nothing is persisted until this service returns without throwing.
 *
 * Call sequence:
 * 1. GET /orgs/{githubOrgName} → verifies org existence + PAT validity
 * 2. GET /orgs/{githubOrgName}/repos → verifies the PAT has 'repo' scope
 *
 */
@Service
public class GitHubValidationService {

    private final RestTemplate restTemplate;
    private final String githubApiBase;

    public GitHubValidationService(
            RestTemplate restTemplate,
            @Value("${github.api.base-url:https://api.github.com}") String githubApiBase) {
        this.restTemplate = restTemplate;
        this.githubApiBase = githubApiBase;
    }

    /**
     * Result record returned by validate(). Contains whether the PAT is valid
     * and the optional expiry date extracted from the GitHub response header.
     */
    public record GitHubValidationResult(boolean valid, java.time.LocalDateTime tokenExpiresAt) {}

    /**
     * Runs the two-step GitHub validation.
     *
     * @throws GitHubValidationException on any failure (401 → invalid PAT,
     *                                   404 → org not found,
     *                                   403 on second call → missing 'repo' scope)
     */
    public GitHubValidationResult validate(String githubOrgName, String githubPat) {
        var headers = new HttpHeaders();
        // GitHub REST API requires a User-Agent header
        headers.set(HttpHeaders.USER_AGENT, "SPM-Senior-App");
        // GitHub REST API v3 accepts a PAT as a Bearer token.
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + githubPat);
        var entity = new HttpEntity<>(headers);

        // Step 1: verify org existence and PAT validity — also extracts expiry header
        java.time.LocalDateTime expiresAt = callStep1OrgExists(githubOrgName, entity);

        // Step 2: verify the PAT has 'repo' scope
        // Only reached when Step 1 succeeds (200 OK).
        callStep2RepoScope(githubOrgName, entity);

        return new GitHubValidationResult(true, expiresAt);
    }

    /**
     * GET /orgs/{githubOrgName}
     * 401 → invalid PAT, 404 → org not found, anything else → rethrow as
     * unreachable. Returns the value of GitHub-Authentication-Token-Expiration header if present.
     */
    private java.time.LocalDateTime callStep1OrgExists(String githubOrgName, HttpEntity<?> entity) {
        var url = githubApiBase + "/orgs/" + githubOrgName;
        try {
            ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.GET, entity, Void.class);
            // Fine-grained PATs expose expiry via this header
            String expiryHeader = response.getHeaders().getFirst("GitHub-Authentication-Token-Expiration");
            if (expiryHeader != null && !expiryHeader.isBlank()) {
                // Format: "2026-06-01 00:00:00 UTC"
                try {
                    String datePart = expiryHeader.replace(" UTC", "").trim();
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    return java.time.LocalDateTime.parse(datePart, formatter);
                } catch (Exception e) {
                    return null;
                }
            }
            return null;

        } catch (HttpClientErrorException ex) {
            HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());

            if (status == HttpStatus.UNAUTHORIZED) {
                throw new GitHubValidationException("GitHub validation failed: PAT is invalid or expired");
            }

            if (status == HttpStatus.NOT_FOUND) {
                throw new GitHubValidationException(
                        "GitHub validation failed: Organization '" + githubOrgName + "' not found");
            }

            // Any other 4xx from the first call — treat as a PAT / auth problem.
            throw new GitHubValidationException("GitHub validation failed: PAT is invalid or expired");

        } catch (ResourceAccessException ex) {
            // Timeout or network failure on the first call — skip Step 2 (fail-fast).
            throw new GitHubValidationException("GitHub validation failed: PAT is invalid or expired");
        }
    }

    /**
     * GET /orgs/{githubOrgName}/repos?per_page=1
     * GitHub returns 403 (not 401) when the PAT lacks 'repo' scope.
     */
    private void callStep2RepoScope(String githubOrgName, HttpEntity<?> entity) {
        var url = githubApiBase + "/orgs/" + githubOrgName + "/repos?per_page=1";
        try {
            restTemplate.exchange(url, HttpMethod.GET, entity, Void.class);

        } catch (HttpClientErrorException ex) {
            HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());

            if (status == HttpStatus.FORBIDDEN) {
                // GitHub returns 403 specifically when the PAT is missing the 'repo' scope.
                throw new GitHubValidationException("GitHub validation failed: PAT lacks required 'repo' scope");
            }

            // Unexpected error on the second call.
            throw new GitHubValidationException("GitHub validation failed: PAT lacks required 'repo' scope");

        } catch (ResourceAccessException ex) {
            // Timeout on the second call — scope check inconclusive; reject to be safe.
            throw new GitHubValidationException("GitHub validation failed: PAT lacks required 'repo' scope");
        }
    }
}
