package com.senior.spm.service.dto;

public record JiraIssueDto(
        String issueKey,
        String assigneeEmail,
        Integer storyPoints,
        String description
) {
}