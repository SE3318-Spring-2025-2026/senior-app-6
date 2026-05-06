package com.senior.spm.controller.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDate;

@Data
public class BindJiraRequest {

    @NotBlank(message = "JIRA space URL is required")
    private String jiraSpaceUrl;

    @NotBlank(message = "JIRA email is required")
    private String jiraEmail;

    @NotBlank(message = "JIRA project key is required")
    private String jiraProjectKey;

    @NotBlank(message = "JIRA API token is required")
    private String jiraApiToken;

    private LocalDate jiraTokenExpiresAt;
}
