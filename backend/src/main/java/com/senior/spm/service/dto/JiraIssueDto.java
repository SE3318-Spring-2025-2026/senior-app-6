package com.senior.spm.service.dto;

public record JiraIssueDto(
        String issueKey,
        String assigneeEmail,
        Double storyPoints,
        String description
) {
}