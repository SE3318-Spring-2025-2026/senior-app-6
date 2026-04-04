package com.senior.spm.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.senior.spm.exception.JiraValidationException;

/**
 * Validates JIRA credentials by making a single live HTTP call to the JIRA REST
 * API.
 * Called by GroupService.bindJira()
 * Nothing is persisted until this service returns without throwing.
 *
 */
@Service
public class JiraValidationService {

    private final RestTemplate restTemplate;

    public JiraValidationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Performs a single GET against:
     * {jiraSpaceUrl}/rest/api/3/project/{jiraProjectKey}
     *
     * @throws JiraValidationException on any failure (401/403 → invalid token,
     *                                 404 → project key not found,
     *                                 timeout/network → unreachable)
     */
    public void validate(String jiraSpaceUrl, String jiraProjectKey, String jiraApiToken) {
        var url = jiraSpaceUrl.stripTrailing() + "/rest/api/3/project/" + jiraProjectKey;

        var headers = new HttpHeaders();
        // JIRA Cloud accepts a Bearer token; on-prem may need Basic – Bearer covers
        // both cases here.
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + jiraApiToken);
        var entity = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(url, HttpMethod.GET, entity, Void.class);

        } catch (HttpClientErrorException ex) {
            HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());

            if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                // JIRA returns 401 for bad tokens and 403 for insufficient permissions.
                throw new JiraValidationException("JIRA validation failed: API token is invalid or expired");
            }

            if (status == HttpStatus.NOT_FOUND) {
                // The project key does not exist in the given JIRA space.
                throw new JiraValidationException(
                        "JIRA validation failed: Project key '" + jiraProjectKey + "' not found");
            }

            // Any other 4xx — treat as unreachable / misconfigured URL.
            throw new JiraValidationException("JIRA validation failed: JIRA space URL is unreachable");

        } catch (ResourceAccessException ex) {
            // Covers connection timeout, read timeout, DNS failure, etc.
            throw new JiraValidationException("JIRA validation failed: JIRA space URL is unreachable");
        }
    }
}
