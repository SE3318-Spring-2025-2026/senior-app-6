// TODO: Issue #49 — [Backend] Tool Binding & Encryption Orchestration
package com.senior.spm.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BindJiraRequest {

    @NotBlank(message = "JIRA space URL is required")
    private String jiraSpaceUrl;

    @NotBlank(message = "JIRA project key is required")
    private String jiraProjectKey;

    @NotBlank(message = "JIRA API token is required")
    private String jiraApiToken;
}
