package com.senior.spm.controller.response;

import java.util.UUID;

import lombok.Data;

/**
 * Response DTO returned by both {@code POST /api/groups/{groupId}/jira} and
 * {@code POST /api/groups/{groupId}/github} after a successful tool binding.
 *
 * <p>JIRA bind response populates: {@code groupId, status, jiraSpaceUrl,
 * jiraProjectKey, jiraBound, githubBound}.
 * <p>GitHub bind response populates: {@code groupId, status, githubOrgName,
 * jiraBound, githubBound}.
 *
 * <p>Fields irrelevant to the current binding operation are left {@code null}.
 * Encrypted fields (JIRA API token, GitHub PAT) are never included — only
 * non-sensitive metadata is returned.
 *
 * <p>Issue: #49 — [Backend] Tool Binding &amp; Encryption Orchestration.
 */
@Data
public class BindToolResponse {

    private UUID groupId;
    private String status;

    // JIRA fields — populated on POST /api/groups/{groupId}/jira
    private String jiraSpaceUrl;
    private String jiraProjectKey;

    // GitHub fields — populated on POST /api/groups/{groupId}/github
    private String githubOrgName;

    // Binding flags — always populated so the client knows the full binding state
    private Boolean jiraBound;
    private Boolean githubBound;
}
