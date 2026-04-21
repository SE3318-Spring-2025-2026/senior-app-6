package com.senior.spm.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.service.dto.JiraIssueDto;

@Service
public class JiraSprintService {

    private static final Logger log = LoggerFactory.getLogger(JiraSprintService.class);
    private final RestClient restClient;
    private final EncryptionService encryptionService;

    public JiraSprintService(RestClient.Builder restClientBuilder, EncryptionService encryptionService) {
        this.restClient = restClientBuilder.build();
        this.encryptionService = encryptionService;
    }

    public List<JiraIssueDto> fetchSprintStories(ProjectGroup group, String jiraSprintId) {
        if (group.getJiraSpaceUrl() == null || group.getJiraEmail() == null || group.getEncryptedJiraToken() == null) {
            log.warn("JIRA credentials or configurations missing for group {}", group.getId());
            return Collections.emptyList();
        }

        try {
            String decryptedToken = encryptionService.decrypt(group.getEncryptedJiraToken());
            String authString = group.getJiraEmail().strip() + ":" + decryptedToken;
            String base64Auth = Base64.getEncoder().encodeToString(authString.getBytes(StandardCharsets.UTF_8));
            
            String baseUrl = group.getJiraSpaceUrl().strip().replaceAll("/+$", "");
            
            List<JiraIssueDto> dtos = new ArrayList<>();
            int startAt = 0;
            boolean hasMore = true;

            while (hasMore) {
                String url = baseUrl + "/rest/agile/1.0/sprint/" + jiraSprintId + "/issue?startAt=" + startAt;

                JsonNode response = restClient.get()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + base64Auth)
                        .header(HttpHeaders.ACCEPT, "application/json")
                        .retrieve()
                        .body(JsonNode.class);

                if (response == null || !response.hasNonNull("issues")) {
                    break;
                }

                for (JsonNode issue : response.get("issues")) {
                    String issueKey = issue.path("key").asText(null);
                    if (issueKey == null) {
                        continue;
                    }

                    JsonNode fields = issue.path("fields");
                    
                    String assignee = null;
                    JsonNode assigneeNode = fields.path("assignee");
                    if (assigneeNode.isObject()) {
                        if (assigneeNode.hasNonNull("emailAddress")) {
                            assignee = assigneeNode.path("emailAddress").asText(null);
                        } else if (assigneeNode.hasNonNull("displayName")) {
                            assignee = assigneeNode.path("displayName").asText(null);
                        }
                    }

                    Integer storyPoints = null;
                    if (fields.hasNonNull("customfield_10016")) {
                        storyPoints = fields.get("customfield_10016").asInt();
                    }

                    String description = null;
                    JsonNode descNode = fields.path("description");
                    if (!descNode.isMissingNode() && !descNode.isNull()) {
                        if (descNode.isObject()) {
                            description = descNode.toString();
                        } else {
                            description = descNode.asText(null);
                        }
                    }

                    dtos.add(new JiraIssueDto(issueKey, assignee, storyPoints, description));
                }
                
                int maxResults = response.path("maxResults").asInt(50);
                int total = response.path("total").asInt(0);
                startAt += maxResults;

                if (startAt >= total || maxResults == 0) {
                    hasMore = false;
                }
            }
            
            return dtos;

        } catch (HttpClientErrorException ex) {
            log.warn("JIRA API returned HTTP {} for group {} when fetching sprint {}", 
                    ex.getStatusCode().value(), group.getId(), jiraSprintId);
            return Collections.emptyList();
        } catch (RestClientException ex) {
            log.warn("Network or REST client error fetching sprint stories for group {}: {}", 
                    group.getId(), ex.getMessage());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.warn("Unexpected error fetching sprint stories for group {}", group.getId(), ex);
            return Collections.emptyList();
        }
    }
}