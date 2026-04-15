package com.senior.spm.service;

import java.util.Base64;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.senior.spm.exception.JiraValidationException;

/**
 * Validates JIRA credentials by making a single live HTTP call to the JIRA REST API.
 * Called by GroupService.bindJira() — nothing is persisted until this returns without throwing.
 */
@Service
public class JiraValidationService {

    private final RestTemplate restTemplate;

    public JiraValidationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Validates JIRA credentials using Basic Auth (Jira Cloud) or Bearer token (Jira Server).
     * Uses the project search endpoint which works reliably across all Jira Cloud project types
     * (classic and next-gen/team-managed).
     *
     * @param jiraEmail Atlassian account email — required for Jira Cloud Basic Auth.
     *                  If null, falls back to Bearer token for Jira Server compatibility.
     * @throws JiraValidationException on any failure (401/403 → invalid token,
     *                                 project not found, timeout/network → unreachable)
     */
    @SuppressWarnings("unchecked")
    public void validate(String jiraSpaceUrl, String jiraEmail, String jiraProjectKey, String jiraApiToken) {
        String baseUrl = jiraSpaceUrl.strip().replaceAll("/+$", "");
        String trimmedKey = jiraProjectKey.strip().toUpperCase();

        // Use the search endpoint — more reliable than /project/{key} for all Jira Cloud project types
        String url = baseUrl + "/rest/api/3/project/search?keys=" + trimmedKey;

        var headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        // Jira Cloud requires Basic Auth (email:token). Bearer is kept for Jira Server.
        if (jiraEmail != null && !jiraEmail.isBlank()) {
            String authString = jiraEmail.strip() + ":" + jiraApiToken;
            String base64Auth = Base64.getEncoder().encodeToString(authString.getBytes(StandardCharsets.UTF_8));
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + base64Auth);
        } else {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + jiraApiToken);
        }

        var entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, (Class<Map<String, Object>>) (Class<?>) Map.class);

            Map<String, Object> body = response.getBody();
            int total = body != null ? ((Number) body.getOrDefault("total", 0)).intValue() : 0;
            if (total == 0) {
                throw new JiraValidationException(
                        "JIRA validation failed: Project key '" + trimmedKey + "' not found in this Jira workspace");
            }

        } catch (JiraValidationException ex) {
            throw ex;

        } catch (HttpClientErrorException ex) {
            HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());

            if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                throw new JiraValidationException("JIRA validation failed: API token is invalid or expired");
            }

            throw new JiraValidationException("JIRA validation failed: JIRA space URL is unreachable");

        } catch (ResourceAccessException ex) {
            throw new JiraValidationException("JIRA validation failed: JIRA space URL is unreachable");
        }
    }
}
