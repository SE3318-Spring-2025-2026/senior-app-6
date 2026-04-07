package com.senior.spm.controller.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GroupSummaryResponse {
    /**
     * Unique identifier (UUID) of the project group.
     */
    private UUID id;

    /**
     * Human-readable name of the project group.
     */
    private String groupName;

    /**
     * Term identifier for which this group was created
     * (e.g., "2024-FALL", "2025-SPRING").
     */
    private String termId;

    /**
     * Current status of the group: FORMING, ACTIVE, DISBANDED, etc.
     */
    private String status;

    /**
     * Current count of group members (does not include pending invitations).
     */
    private int memberCount;

    /**
     * Flag indicating whether this group has been bound to Jira
     * (i.e., has both jiraSpaceUrl and jiraProjectKey configured).
     */
    private boolean jiraBound;

    /**
     * Flag indicating whether this group has been bound to GitHub
     * (i.e., has githubOrgName configured).
     */
    private boolean githubBound;
}
