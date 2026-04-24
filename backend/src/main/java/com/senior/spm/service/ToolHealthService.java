package com.senior.spm.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senior.spm.entity.ProjectGroup;
import com.senior.spm.repository.ProjectGroupRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Background service that periodically validates the health of bound tool tokens.
 * 
 * <p>Addresses PR #183 blocker: provides a path for tokenValid flags to become false
 * if a token is revoked or expires without a manual re-bind attempt.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ToolHealthService {

    private final ProjectGroupRepository projectGroupRepository;
    private final EncryptionService encryptionService;
    private final JiraValidationService jiraValidationService;
    private final GitHubValidationService gitHubValidationService;

    /**
     * Daily background job to verify all bound tool tokens.
     * 
     * Runs daily at 2:00 AM by default.
     */
    @Scheduled(cron = "${tool.health-check.cron:0 0 2 * * *}")
    @Transactional
    public void checkAllToolHealth() {
        log.info("Starting background tool health check...");
        
        var groups = projectGroupRepository.findAll();
        int jiraChecked = 0;
        int githubChecked = 0;

        for (var group : groups) {
            // 1. Check JIRA health if bound
            if (group.getEncryptedJiraToken() != null) {
                jiraChecked++;
                checkJiraHealth(group);
            }

            // 2. Check GitHub health if bound
            if (group.getEncryptedGithubPat() != null) {
                githubChecked++;
                checkGitHubHealth(group);
            }
        }

        log.info("Tool health check completed. Groups checked: JIRA={}, GitHub={}", jiraChecked, githubChecked);
    }

    private void checkJiraHealth(ProjectGroup group) {
        try {
            String plainToken = encryptionService.decrypt(group.getEncryptedJiraToken());
            jiraValidationService.validate(
                group.getJiraSpaceUrl(), 
                group.getJiraEmail(), 
                group.getJiraProjectKey(), 
                plainToken
            );
            group.setJiraTokenValid(true);
        } catch (Exception e) {
            log.warn("JIRA token validation failed for group '{}': {}", group.getGroupName(), e.getMessage());
            group.setJiraTokenValid(false);
        }
        projectGroupRepository.save(group);
    }

    private void checkGitHubHealth(ProjectGroup group) {
        try {
            String plainPat = encryptionService.decrypt(group.getEncryptedGithubPat());
            gitHubValidationService.validate(
                group.getGithubOrgName(), 
                plainPat, 
                group.getGithubRepoName()
            );
            group.setGithubTokenValid(true);
        } catch (Exception e) {
            log.warn("GitHub PAT validation failed for group '{}': {}", group.getGroupName(), e.getMessage());
            group.setGithubTokenValid(false);
        }
        projectGroupRepository.save(group);
    }
}
